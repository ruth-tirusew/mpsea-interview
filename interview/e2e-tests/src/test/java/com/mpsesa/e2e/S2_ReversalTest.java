package com.mpsesa.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class S2_ReversalTest {
    @BeforeEach
    void reset() {
        Api.resetAll();
    }

    private static Api.Resp reverse(String base, String transferId) {
        return Api.post(base, "/mpesa/v1/p2p/transfers/" + transferId + "/reversal", null, Map.of("reason", "customer dispute"));
    }

    @Test
    void s2_1_thirty_concurrent_reversals_move_money_once() {
        Api.Resp t = Api.transfer(Api.instance(0), "W001", "W002", 5_000);
        String id = t.body().get("transfer_id").asText();
        List<Api.Resp> rs = Api.concurrently(30, i -> reverse(Api.instance(i), id));
        assertThat(Api.count(rs, 200)).isEqualTo(1);
        assertThat(Api.count(rs, 409)).isEqualTo(29);
        rs.stream().filter(r -> r.status() == 409).forEach(r -> assertThat(r.error()).isEqualTo("ALREADY_REVERSED"));
        Api.Resp ok = rs.stream().filter(r -> r.status() == 200).findFirst().orElseThrow();
        assertThat(ok.body().get("status").asText()).isEqualTo("REVERSED");
        assertThat(ok.body().get("amount").asLong()).isEqualTo(5_000);
        assertThat(Api.balance("W001")).isEqualTo(1_000_000);
        assertThat(Api.balance("W002")).isEqualTo(1_000_000);
    }

    @Test
    void s2_2_reversal_races_a_spend_of_the_same_money() {
        Api.Resp first = Api.transfer(Api.instance(0), "W020", "W021", 300_000);
        String id = first.body().get("transfer_id").asText();
        assertThat(Api.transfer(Api.instance(1), "W021", "W022", 1_000_000).status()).isEqualTo(201);
        assertThat(Api.balance("W021")).isEqualTo(300_000);

        List<Api.Resp> rs = Api.concurrently(2, i -> i == 0
                ? reverse(Api.instance(0), id)
                : Api.transfer(Api.instance(1), "W021", "W023", 300_000));
        boolean reversalWon = rs.get(0).status() == 200;
        boolean transferWon = rs.get(1).status() == 201;
        assertThat(reversalWon ^ transferWon).as("exactly one succeeds").isTrue();
        assertThat(reversalWon ? rs.get(1).error() : rs.get(0).error()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(Api.balance("W021")).isZero();
        assertThat(Api.sumAllBalances()).isEqualTo(Api.SEED_TOTAL);
    }

    @Test
    void s2_3_reversal_credits_a_wallet_frozen_after_the_transfer() {
        Api.Resp t = Api.transfer(Api.instance(0), "W030", "W031", 5_000);
        String id = t.body().get("transfer_id").asText();
        Api.Resp frozen = Api.put(Api.instance(1), "/mpesa/v1/wallets/W030/status", Map.of("status", "FROZEN", "reason", "test"));
        assertThat(frozen.status()).isEqualTo(200);
        Api.Resp r = reverse(Api.instance(0), id);
        assertThat(r.status()).isEqualTo(200);
        assertThat(Api.balance("W030")).isEqualTo(1_000_000);
        assertThat(Api.balance("W031")).isEqualTo(1_000_000);
    }

    @Test
    void reversal_error_cases() {
        assertThat(reverse(Api.instance(0), "T-999999").status()).isEqualTo(404);
        assertThat(reverse(Api.instance(0), "garbage").status()).isEqualTo(404);
        Api.Resp t = Api.transfer(Api.instance(0), "W001", "W002", 5_000);
        String id = t.body().get("transfer_id").asText();
        Api.put(Api.instance(0), "/mpesa/v1/wallets/W002/status", Map.of("status", "FROZEN"));
        Api.Resp r = reverse(Api.instance(1), id);
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.error()).isEqualTo("WALLET_NOT_ACTIVE");
        assertThat(Api.balance("W001")).isEqualTo(995_000);
    }
}
