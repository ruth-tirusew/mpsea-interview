package com.mpsesa.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class S5_CallbackTest {
    @BeforeEach
    void reset() {
        Api.resetAll();
    }

    @Test
    void s5_1_every_callback_delivered_two_or_three_times_applies_once() {
        BankAdmin.configure(Map.of("duplicateCallbackRate", 1.0));
        List<String> ids = Payouts.createMany(40, 5_000, new ArrayList<>());
        Payouts.awaitFinal(ids, Duration.ofMinutes(2), Api.INSTANCES);
        Payouts.assertReconciled(ids, Api.INSTANCES.get(0), 5_000);
    }

    @Test
    void s5_2_default_bank_every_status_matches_the_bank() {
        List<String> ids = Payouts.createMany(100, 5_000, new ArrayList<>());
        Payouts.awaitFinal(ids, Duration.ofMinutes(2), Api.INSTANCES);
        Payouts.assertReconciled(ids, Api.INSTANCES.get(1), 5_000);
    }

    @Test
    void callback_contract_unknown_payout_and_conflicts() {
        String b = Api.instance(0);
        Api.Resp unknown = Api.callback(b, Map.of("event_id", "e1", "payout_id", "P-unknown", "bank_ref", "BR1", "status", "SUCCEEDED"));
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.error()).isEqualTo("PAYOUT_NOT_FOUND");

        BankAdmin.configure(Map.of("noResponseRate", 0.0, "rejectRate", 0.0, "callbackFailRate", 0.0));
        List<String> ids = Payouts.createMany(1, 5_000, new ArrayList<>());
        Payouts.awaitFinal(ids, Duration.ofMinutes(1), Api.INSTANCES);
        String id = ids.get(0);
        assertThat(Api.get(b, "/mpesa/v1/payouts/" + id).body().get("status").asText()).isEqualTo("SUCCEEDED");

        // a different event that agrees: no change, no conflict
        Api.Resp agree = Api.callback(Api.instance(1), Map.of("event_id", "e2", "payout_id", id, "bank_ref", "BR1", "status", "SUCCEEDED"));
        assertThat(agree.status()).isEqualTo(200);
        assertThat(Api.get(b, "/mpesa/v1/payouts/" + id).body().get("conflict").asBoolean()).isFalse();

        // a contradicting one: 200, status untouched, conflict true, money untouched
        Api.Resp clash = Api.callback(b, Map.of("event_id", "e3", "payout_id", id, "bank_ref", "BR1", "status", "FAILED"));
        assertThat(clash.status()).isEqualTo(200);
        assertThat(Api.get(b, "/mpesa/v1/payouts/" + id).body().get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(Api.get(b, "/mpesa/v1/payouts/" + id).body().get("conflict").asBoolean()).isTrue();
        assertThat(Api.balance("M001")).isEqualTo(50_000_000 - 5_000);
        assertThat(Api.wallet("M001").get("reserved").asLong()).isZero();

        assertThat(Api.callback(b, Map.of("event_id", "e4", "payout_id", id, "status", "MAYBE")).status())
                .isEqualTo(400);
    }
}
