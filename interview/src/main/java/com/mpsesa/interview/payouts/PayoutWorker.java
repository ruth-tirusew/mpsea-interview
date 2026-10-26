package com.mpsesa.interview.payouts;

import com.mpsesa.interview.bank.BankClient;
import com.mpsesa.interview.bank.BankClient.BankStatus;
import com.mpsesa.interview.bank.BankClient.SubmitResult;
import com.mpsesa.interview.core.WalletProperties;
import com.mpsesa.interview.payouts.domain.Payout;
import com.mpsesa.interview.payouts.domain.PayoutState;
import com.mpsesa.interview.payouts.repository.PayoutRepository;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Submits payouts to the bank and reconciles unknown ones. Work lives in the payouts table, not in
 * memory: any instance claims due rows with a lease (FOR UPDATE SKIP LOCKED), so when an instance is
 * killed its leases expire and the other instance finishes the work. Bank calls run on a bounded
 * private pool and hold no DB connection and no Tomcat thread.
 *
 * Payouts are only ever reported FAILED on a definite bank answer (REJECTED, FAILED callback or status).
 * A timeout is UNKNOWN: we ask the bank, and if it has no record we resubmit; it dedupes on payout_id.
 */
@Component
public class PayoutWorker {
    private static final Logger log = LoggerFactory.getLogger(PayoutWorker.class);
    private static final int WORKERS = 32;
    private static final Duration LEASE = Duration.ofSeconds(20);      // > 8 s bank timeout + DB calls
    private static final Duration RECONCILE_AFTER_ACCEPT = Duration.ofSeconds(12); // callback normally lands in 1-10 s
    private static final Duration RETRY = Duration.ofSeconds(2);

    private final PayoutRepository payouts;
    private final PayoutService service;
    private final BankClient bank;
    private final TransactionTemplate tx;
    private final String instanceId;
    private final Semaphore slots = new Semaphore(WORKERS);
    private final ExecutorService pool = Executors.newFixedThreadPool(WORKERS);

    public PayoutWorker(PayoutRepository payouts, PayoutService service, BankClient bank, TransactionTemplate tx,
                        WalletProperties props) {
        this.payouts = payouts;
        this.service = service;
        this.bank = bank;
        this.tx = tx;
        this.instanceId = props.instanceId();
    }

    private record Claim(String id, PayoutState state) {
    }

    @Scheduled(fixedDelay = 250)
    void poll() {
        try {
            int free = slots.availablePermits();
            if (free == 0) {
                return;
            }
            List<Claim> claimed = tx.execute(s -> {
                Instant now = Instant.now();
                List<Payout> due = payouts.claimDue(now, free);
                due.forEach(p -> p.lease(instanceId, now.plus(LEASE)));
                return due.stream().map(p -> new Claim(p.getPayoutId(), p.getState())).toList();
            });
            for (Claim c : claimed) {
                slots.acquireUninterruptibly();
                pool.execute(() -> {
                    try {
                        process(c);
                    } catch (RuntimeException e) {
                        log.warn("payout {} step failed, lease will expire and retry: {}", c.id(), e.toString());
                    } finally {
                        slots.release();
                    }
                });
            }
        } catch (RuntimeException e) {
            log.warn("payout poll failed: {}", e.getClass().getSimpleName());
        }
    }

    private void process(Claim c) {
        if (c.state() == PayoutState.RESERVED) {
            submit(c.id());
        } else {
            reconcile(c.id());   // SUBMITTING (previous owner may have died mid-call), SUBMITTED, UNKNOWN
        }
    }

    private void submit(String id) {
        PayoutService.SubmitData d = service.beginSubmit(id).orElse(null);
        if (d == null) {
            return;
        }
        SubmitResult r = bank.submit(d.payoutId(), d.bankAccount(), d.amount());
        switch (r.outcome()) {
            case ACCEPTED -> service.accepted(id, r.bankRef(), RECONCILE_AFTER_ACCEPT);
            case REJECTED -> service.applyFinal(id, PayoutState.FAILED, null);
            case UNKNOWN -> service.unknown(id, RETRY);
        }
    }

    private void reconcile(String id) {
        if (!service.isOpen(id)) {
            return;
        }
        BankStatus s = bank.status(id);
        switch (s) {
            case SUCCEEDED -> service.applyFinal(id, PayoutState.SUCCEEDED, null);
            case FAILED, REJECTED -> service.applyFinal(id, PayoutState.FAILED, null);
            case NOT_FOUND -> service.requeueForSubmit(id);
            case IN_PROGRESS, UNKNOWN -> service.pollLater(id, Duration.ofSeconds(3));
        }
    }

    @PreDestroy
    void stop() {
        pool.shutdownNow();
    }
}
