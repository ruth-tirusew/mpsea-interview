package com.mpsesa.interview.payouts;

import com.mpsesa.interview.accounts.domain.WalletAccount;
import com.mpsesa.interview.accounts.domain.WalletAccountStatusEnum;
import com.mpsesa.interview.accounts.repository.WalletAccountRepository;
import com.mpsesa.interview.core.ApiException;
import com.mpsesa.interview.core.DbLocks;
import com.mpsesa.interview.core.ErrorCode;
import com.mpsesa.interview.core.Times;
import com.mpsesa.interview.ledger.domain.LedgerEntry;
import com.mpsesa.interview.ledger.repository.LedgerRepository;
import com.mpsesa.interview.payouts.data.PayoutAccepted;
import com.mpsesa.interview.payouts.data.PayoutRequest;
import com.mpsesa.interview.payouts.data.PayoutView;
import com.mpsesa.interview.payouts.domain.Payout;
import com.mpsesa.interview.payouts.domain.PayoutState;
import com.mpsesa.interview.payouts.repository.PayoutRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayoutService {
    public record SubmitData(String payoutId, String bankAccount, long amount) {
    }

    private final PayoutRepository payouts;
    private final WalletAccountRepository wallets;
    private final LedgerRepository ledger;
    private final DbLocks locks;

    public PayoutService(PayoutRepository payouts, WalletAccountRepository wallets, LedgerRepository ledger, DbLocks locks) {
        this.payouts = payouts;
        this.wallets = wallets;
        this.ledger = ledger;
        this.locks = locks;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public PayoutAccepted create(PayoutRequest req) {
        locks.boundWaits();
        WalletAccount w = wallets.lockById(req.walletId())
                .orElseThrow(() -> new ApiException(ErrorCode.WALLET_NOT_FOUND, "wallet " + req.walletId() + " not found"));
        if (w.getStatus() != WalletAccountStatusEnum.ACTIVE) {
            throw new ApiException(ErrorCode.WALLET_NOT_ACTIVE, w.getWalletId() + " must be ACTIVE");
        }
        if (w.available() < req.amount()) {
            throw new ApiException(ErrorCode.INSUFFICIENT_FUNDS,
                    w.getWalletId() + " available " + w.available() + ", requested " + req.amount());
        }
        w.reserve(req.amount());
        Payout p = payouts.save(new Payout("P-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                req.walletId(), req.bankAccount(), req.amount()));
        return new PayoutAccepted(p.getPayoutId(), p.getState().publicStatus());
    }

    @Transactional(readOnly = true)
    public PayoutView get(String payoutId) {
        Payout p = payouts.findById(payoutId)
                .orElseThrow(() -> new ApiException(ErrorCode.PAYOUT_NOT_FOUND, "payout " + payoutId + " not found"));
        return new PayoutView(p.getPayoutId(), p.getWalletId(), p.getAmount(), p.getState().publicStatus(), p.getBankRef(),
                p.isConflict(), Times.iso(p.getUpdatedAt()));
    }

    /**
     * Applies a bank-confirmed final outcome exactly once. SUCCEEDED lowers balance and releases the
     * reservation; FAILED only releases it. A contradicting later outcome never changes a final
     * status; it only sets conflict. Caller holds the payout row lock.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void applyFinal(Payout p, PayoutState target, String bankRef) {
        if (p.getState().isFinal()) {
            if (p.getState() != target) {
                p.markConflict();
            }
            return;
        }
        WalletAccount w = wallets.lockById(p.getWalletId()).orElseThrow(() -> new IllegalStateException("wallet missing"));
        if (target == PayoutState.SUCCEEDED) {
            w.debit(p.getAmount());
            w.release(p.getAmount());
            ledger.save(new LedgerEntry(w.getWalletId(), "PAYOUT", -p.getAmount(), w.getBalance(), p.getPayoutId()));
        } else {
            w.release(p.getAmount());
        }
        p.setState(target);
        if (bankRef != null) {
            p.setBankRef(bankRef);
        }
        p.releaseLease();
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void applyFinal(String payoutId, PayoutState target, String bankRef) {
        locks.boundWaits();
        payouts.lockById(payoutId).ifPresent(p -> applyFinal(p, target, bankRef));
    }

    /** Marks the attempt. Empty when the payout is already final (e.g. an early callback won). */
    @Transactional(noRollbackFor = ApiException.class)
    public Optional<SubmitData> beginSubmit(String payoutId) {
        locks.boundWaits();
        return payouts.lockById(payoutId).filter(p -> !p.getState().isFinal()).map(p -> {
            p.setState(PayoutState.SUBMITTING);
            p.recordAttempt(Instant.now());
            return new SubmitData(p.getPayoutId(), p.getBankAccount(), p.getAmount());
        });
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void accepted(String payoutId, String bankRef, Duration reconcileAfter) {
        update(payoutId, p -> {
            p.setState(PayoutState.SUBMITTED);
            p.setBankRef(bankRef);
            p.recordAttempt(Instant.now().plus(reconcileAfter));
        });
    }

    /** Outcome unknown: ask the bank later. Money stays reserved. */
    @Transactional(noRollbackFor = ApiException.class)
    public void unknown(String payoutId, Duration retryAfter) {
        update(payoutId, p -> {
            p.setState(PayoutState.UNKNOWN);
            p.recordAttempt(Instant.now().plus(retryAfter));
        });
    }

    /** Bank is still working on it: look again later, state unchanged. */
    @Transactional(noRollbackFor = ApiException.class)
    public void pollLater(String payoutId, Duration after) {
        update(payoutId, p -> p.recordAttempt(Instant.now().plus(after)));
    }

    /** Bank has no record of it: submit again (safe, the bank is idempotent on payout_id). */
    @Transactional(noRollbackFor = ApiException.class)
    public void requeueForSubmit(String payoutId) {
        update(payoutId, p -> {
            p.setState(PayoutState.RESERVED);
            p.recordAttempt(Instant.now());
        });
    }

    @Transactional(readOnly = true)
    public boolean isOpen(String payoutId) {
        return payouts.findById(payoutId).map(p -> !p.getState().isFinal()).orElse(false);
    }

    private void update(String payoutId, java.util.function.Consumer<Payout> change) {
        locks.boundWaits();
        payouts.lockById(payoutId).filter(p -> !p.getState().isFinal()).ifPresent(p -> {
            change.accept(p);
            p.releaseLease();
        });
    }
}
