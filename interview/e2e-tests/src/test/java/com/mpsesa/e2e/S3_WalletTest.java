package com.mpsesa.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class S3_WalletTest {
    @BeforeEach
    void reset() {
        Api.resetAll();
    }

    @Test
    void s3_1_freeze_is_a_hard_barrier_for_in_flight_debits() throws Exception {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        AtomicLong frozenBalance = new AtomicLong(-1);
        AtomicLong freezeMillis = new AtomicLong();
        CompletableFuture<Void> freezeDone = new CompletableFuture<>();
        try {
            // started together with the first transfer; fires ~100 ms into the burst
            timer.schedule(() -> {
                try {
                    Api.Resp f = Api.put(Api.instance(0), "/mpesa/v1/wallets/W030/status", Map.of("status", "FROZEN", "reason", "fraud review"));
                    assertThat(f.status()).isEqualTo(200);
                    freezeMillis.set(f.millis());
                    frozenBalance.set(Api.balance("W030"));
                    freezeDone.complete(null);
                } catch (Throwable t) {
                    freezeDone.completeExceptionally(t);
                }
            }, 100, TimeUnit.MILLISECONDS);

            List<Api.Resp> rs = Api.concurrently(300, i -> Api.transfer(Api.instance(i), "W030", Api.wid(40 + i % 50), 1_000));
            freezeDone.get(30, TimeUnit.SECONDS);

            long b = frozenBalance.get();
            assertThat(freezeMillis.get()).isLessThan(2_000);
            assertThat(Api.balance("W030")).isEqualTo(b);
            assertThat(Api.count(rs, 201)).isEqualTo((1_000_000 - b) / 1_000);
            assertThat(Api.fiveXx(rs)).isZero();
            rs.stream().filter(r -> r.status() != 201).forEach(r -> {
                assertThat(r.status()).isEqualTo(422);
                assertThat(r.error()).isEqualTo("WALLET_NOT_ACTIVE");
            });
            assertThat(Api.sumAllBalances()).isEqualTo(Api.SEED_TOTAL);
        } finally {
            timer.shutdownNow();
        }
    }

    @Test
    void s3_2_available_is_always_balance_minus_reserved_while_payouts_run() {
        List<String> ids = Payouts.createMany(20, 5_000, new ArrayList<>());
        AtomicBoolean done = new AtomicBoolean();
        CompletableFuture<Void> finals = CompletableFuture.runAsync(() -> {
            Payouts.awaitFinal(ids, Duration.ofMinutes(2), Api.INSTANCES);
            done.set(true);
        });
        int reads = 0;
        while (!done.get() && !finals.isCompletedExceptionally()) {
            JsonNode w = Api.get(Api.instance(reads++), "/mpesa/v1/wallets/M001").body();
            assertThat(w.get("available").asLong()).isEqualTo(w.get("balance").asLong() - w.get("reserved").asLong());
            assertThat(w.get("available").asLong()).isGreaterThanOrEqualTo(0);
        }
        finals.join();
        assertThat(reads).isGreaterThan(5);
        Payouts.assertReconciled(ids, Api.INSTANCES.get(0), 5_000);
    }

    @Test
    void status_changes_are_idempotent_and_validated() {
        String b = Api.instance(0);
        Api.Resp same = Api.put(b, "/mpesa/v1/wallets/W010/status", Map.of("status", "ACTIVE"));
        assertThat(same.status()).isEqualTo(200);
        assertThat(same.body().get("status").asText()).isEqualTo("ACTIVE");
        assertThat(Api.put(b, "/mpesa/v1/wallets/W010/status", Map.of("status", "FROZEN")).status()).isEqualTo(200);
        assertThat(Api.put(b, "/mpesa/v1/wallets/W010/status", Map.of("status", "FROZEN")).status()).isEqualTo(200);
        assertThat(Api.get(Api.instance(1), "/mpesa/v1/wallets/W010").body().get("status").asText()).isEqualTo("FROZEN");
        assertThat(Api.put(b, "/mpesa/v1/wallets/W010/status", Map.of("status", "BOGUS")).status()).isEqualTo(400);
        assertThat(Api.put(b, "/mpesa/v1/wallets/W999/status", Map.of("status", "FROZEN")).error()).isEqualTo("WALLET_NOT_FOUND");
        assertThat(Api.get(b, "/mpesa/v1/wallets/W999").status()).isEqualTo(404);
    }
}
