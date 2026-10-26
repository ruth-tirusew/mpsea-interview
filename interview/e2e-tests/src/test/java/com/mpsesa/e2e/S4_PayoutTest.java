package com.mpsesa.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class S4_PayoutTest {
    @BeforeEach
    void reset() {
        Api.resetAll();
    }

    @Test
    void s4_1_and_s4_2_payouts_are_fast_and_do_not_slow_transfers() {
        List<Api.Resp> accepted = new ArrayList<>();
        List<String> ids = Payouts.createMany(60, 5_000, accepted);
        assertThat(Api.count(accepted, 202)).isEqualTo(60);
        assertThat(accepted.stream().map(r -> r.body().get("status").asText()).distinct()).containsExactly("PENDING");
        assertThat(Api.p95(accepted)).isLessThan(500);

        // S4.2: transfers while those 60 payouts are in flight
        List<Api.Resp> transfers = Api.withParallelism(200, 10, i -> Api.transfer(Api.instance(i), "W040", "W041", 1_000));
        assertThat(Api.count(transfers, 201)).isEqualTo(200);
        assertThat(Api.fiveXx(transfers)).isZero();
        assertThat(Api.p95(transfers)).isLessThan(300);
        assertThat(Api.balance("W040")).isEqualTo(800_000);
        assertThat(Api.balance("W041")).isEqualTo(1_200_000);

        Payouts.awaitFinal(ids, Duration.ofMinutes(2), Api.INSTANCES);
        Payouts.assertReconciled(ids, Api.INSTANCES.get(1), 5_000);
        assertThat(Payouts.poolTimeouts("e2e-wallet-1")).isZero();
        assertThat(Payouts.poolTimeouts("e2e-wallet-2")).isZero();
    }

    @Test
    void payout_validation_and_funds() {
        String b = Api.instance(0);
        assertThat(Api.post(b, "/mpesa/v1/payouts", Api.key(), Map.of("wallet_id", "W001", "bank_account", "A", "amount", 0L)).status()).isEqualTo(400);
        assertThat(Api.post(b, "/mpesa/v1/payouts", Api.key(), Map.of("wallet_id", "W999", "bank_account", "A", "amount", 5L)).status()).isEqualTo(404);
        assertThat(Api.post(b, "/mpesa/v1/payouts", Api.key(), Map.of("wallet_id", "W101", "bank_account", "A", "amount", 5L)).error()).isEqualTo("WALLET_NOT_ACTIVE");
        BankAdmin.configure(Map.of("noResponseRate", 0.0, "rejectRate", 0.0));
        Api.Resp ok = Api.post(b, "/mpesa/v1/payouts", Api.key(), Map.of("wallet_id", "W001", "bank_account", "A", "amount", 900_000L));
        assertThat(ok.status()).isEqualTo(202);
        assertThat(Api.wallet("W001").get("available").asLong()).isEqualTo(100_000);   // reserved, balance untouched
        assertThat(Api.wallet("W001").get("balance").asLong()).isEqualTo(1_000_000);
        Api.Resp tooMuch = Api.post(b, "/mpesa/v1/payouts", Api.key(), Map.of("wallet_id", "W001", "bank_account", "A", "amount", 100_001L));
        assertThat(tooMuch.status()).isEqualTo(422);
        assertThat(tooMuch.error()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(Api.get(b, "/mpesa/v1/payouts/P-nope").status()).isEqualTo(404);
    }

    @Test
    void payout_idempotency_replays_the_original_response() {
        String key = Api.key();
        Map<String, Object> body = Map.of("wallet_id", "M001", "bank_account", "ACC-9", "amount", 7_000L);
        List<Api.Resp> rs = Api.concurrently(10, i -> Api.post(Api.instance(i), "/mpesa/v1/payouts", key, body));
        Api.Resp again = Api.post(Api.instance(0), "/mpesa/v1/payouts", key, body);
        List<String> ids = rs.stream().filter(r -> r.status() == 202).map(r -> r.body().get("payout_id").asText()).distinct().toList();
        assertThat(ids).hasSize(1);
        assertThat(again.status()).isEqualTo(202);
        assertThat(again.body().get("payout_id").asText()).isEqualTo(ids.get(0));
        assertThat(Api.wallet("M001").get("reserved").asLong()).isEqualTo(7_000);
        Payouts.awaitFinal(ids, Duration.ofMinutes(2), Api.INSTANCES);
        Payouts.assertReconciled(ids, Api.INSTANCES.get(0), 7_000);
    }
}
