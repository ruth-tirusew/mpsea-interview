package com.mpsesa.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class S1_TransferTest {
    @BeforeEach
    void reset() {
        Api.resetAll();
    }

    @Test
    void s1_1_two_hundred_concurrent_transfers_drain_exactly_one_wallet() {
        List<Api.Resp> rs = Api.concurrently(200, i ->
                Api.transfer(Api.instance(i), "W001", Api.wid(2 + ThreadLocalRandom.current().nextInt(99)), 10_000));
        assertThat(Api.count(rs, 201)).isEqualTo(100);
        assertThat(Api.count(rs, 422)).isEqualTo(100);
        assertThat(rs.stream().filter(r -> r.status() == 422).map(Api.Resp::error).collect(Collectors.toSet()))
                .containsExactly("INSUFFICIENT_FUNDS");
        assertThat(Api.balance("W001")).isZero();
        assertThat(Api.sumAllBalances()).isEqualTo(Api.SEED_TOTAL);
    }

    @Test
    void s1_2_twenty_identical_requests_debit_once() {
        String key = Api.key();
        Map<String, Object> body = Map.of("from_wallet", "W001", "to_wallet", "W002", "amount", 10_000L, "reference", "same");
        List<Api.Resp> rs = Api.concurrently(20, i -> Api.post(Api.instance(i), "/mpesa/v1/p2p/transfers", key, body));
        Api.Resp last = Api.post(Api.instance(1), "/mpesa/v1/p2p/transfers", key, body);

        List<Api.Resp> created = rs.stream().filter(r -> r.status() == 201).toList();
        assertThat(created).isNotEmpty();
        // everything that is not a 201 may only be the documented "still running" answer
        rs.stream().filter(r -> r.status() != 201).forEach(r -> {
            assertThat(r.status()).isEqualTo(409);
            assertThat(r.error()).isEqualTo("REQUEST_IN_PROGRESS");
        });
        Set<String> ids = created.stream().map(r -> r.body().get("transfer_id").asText()).collect(Collectors.toSet());
        assertThat(ids).hasSize(1);
        assertThat(last.status()).isEqualTo(201);
        assertThat(last.body().get("transfer_id").asText()).isEqualTo(ids.iterator().next());
        assertThat(Api.balance("W001")).isEqualTo(990_000);
        assertThat(Api.balance("W002")).isEqualTo(1_010_000);
    }

    @Test
    void s1_2b_same_key_different_body_conflicts() {
        String key = Api.key();
        Api.Resp a = Api.post(Api.instance(0), "/mpesa/v1/p2p/transfers", key,
                Map.of("from_wallet", "W001", "to_wallet", "W002", "amount", 100L, "reference", "x"));
        Api.Resp b = Api.post(Api.instance(1), "/mpesa/v1/p2p/transfers", key,
                Map.of("from_wallet", "W001", "to_wallet", "W002", "amount", 200L, "reference", "x"));
        assertThat(a.status()).isEqualTo(201);
        assertThat(b.status()).isEqualTo(409);
        assertThat(b.error()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(Api.balance("W001")).isEqualTo(999_900);
    }

    @Test
    void s1_3_opposing_transfers_do_not_deadlock() {
        long t0 = System.nanoTime();
        List<Api.Resp> rs = Api.concurrently(100, i -> i % 2 == 0
                ? Api.transfer(Api.instance(i), "W001", "W002", 1_000)
                : Api.transfer(Api.instance(i), "W002", "W001", 1_000));
        long seconds = (System.nanoTime() - t0) / 1_000_000_000;
        assertThat(Api.count(rs, 201)).isEqualTo(100);
        assertThat(Api.fiveXx(rs)).isZero();
        assertThat(seconds).isLessThanOrEqualTo(10);
        assertThat(Api.balance("W001")).isEqualTo(1_000_000);
        assertThat(Api.balance("W002")).isEqualTo(1_000_000);
    }

    @Test
    void validation_and_error_contract() {
        String b = Api.instance(0);
        assertThat(Api.transfer(b, "W001", "W001", 100).error()).isEqualTo("SAME_WALLET");
        assertThat(Api.transfer(b, "W001", "W999", 100).status()).isEqualTo(404);
        assertThat(Api.transfer(b, "W001", "W101", 100).error()).isEqualTo("WALLET_NOT_ACTIVE");
        assertThat(Api.transfer(b, "W101", "W001", 100).error()).isEqualTo("WALLET_NOT_ACTIVE");
        assertThat(Api.transfer(b, "W001", "W002", 0).status()).isEqualTo(400);
        assertThat(Api.transfer(b, "W001", "W002", 5_000_001).status()).isEqualTo(400);
        Api.Resp noKey = Api.post(b, "/mpesa/v1/p2p/transfers", null,
                Map.of("from_wallet", "W001", "to_wallet", "W002", "amount", 100L, "reference", "x"));
        assertThat(noKey.status()).isEqualTo(400);
        Api.Resp longKey = Api.post(b, "/mpesa/v1/p2p/transfers", "k".repeat(65),
                Map.of("from_wallet", "W001", "to_wallet", "W002", "amount", 100L, "reference", "x"));
        assertThat(longKey.status()).isEqualTo(400);
        Api.Resp longRef = Api.post(b, "/mpesa/v1/p2p/transfers", Api.key(),
                Map.of("from_wallet", "W001", "to_wallet", "W002", "amount", 100L, "reference", "r".repeat(65)));
        assertThat(longRef.status()).isEqualTo(400);
        assertThat(Api.balance("W001")).isEqualTo(1_000_000);
    }
}
