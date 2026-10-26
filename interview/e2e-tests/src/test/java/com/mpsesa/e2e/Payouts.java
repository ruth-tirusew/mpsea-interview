package com.mpsesa.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;

/** Shared payout scenario helpers. */
final class Payouts {
    private Payouts() {
    }

    static Api.Resp create(String base, String wallet, long amount) {
        return Api.post(base, "/mpesa/v1/payouts", Api.key(),
                Map.of("wallet_id", wallet, "bank_account", "ACC-001", "amount", amount));
    }

    static List<String> createMany(int n, long amount, List<Api.Resp> responsesOut) {
        List<Api.Resp> rs = Api.concurrently(n, i -> create(Api.instance(i), "M001", amount));
        responsesOut.addAll(rs);
        List<String> ids = new ArrayList<>();
        rs.forEach(r -> ids.add(r.body().path("payout_id").asText()));
        return ids;
    }

    /** Polls until no payout is PENDING. Uses only the given instances (one may have been killed). */
    static void awaitFinal(List<String> ids, Duration atMost, List<String> bases) {
        Awaitility.await().atMost(atMost).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            for (String id : ids) {
                JsonNode p = Api.get(bases.get(0), "/mpesa/v1/payouts/" + id).body();
                assertThat(p.get("status").asText()).as("payout " + id).isNotEqualTo("PENDING");
            }
        });
    }

    /**
     * The whole reconciliation: our SUCCEEDED total equals what the fake bank paid, M001 dropped by exactly
     * that, nothing is left reserved, and no payout saw a contradicting callback.
     */
    static long assertReconciled(List<String> ids, String base, long amountEach) {
        long succeeded = 0;
        for (String id : ids) {
            JsonNode p = Api.get(base, "/mpesa/v1/payouts/" + id).body();
            String st = p.get("status").asText();
            assertThat(st).isIn("SUCCEEDED", "FAILED");
            assertThat(p.get("conflict").asBoolean()).as("conflict " + id).isFalse();
            String bankStatus = Api.get(Api.BANK, "/bank/payouts/" + id).body().get("status").asText();
            if (st.equals("SUCCEEDED")) {
                succeeded += amountEach;
                assertThat(bankStatus).as("bank view of " + id).isEqualTo("SUCCEEDED");
            } else {
                assertThat(bankStatus).as("bank view of " + id).isIn("FAILED", "REJECTED");
            }
        }
        JsonNode m001 = Api.get(base, "/mpesa/v1/wallets/M001").body();
        assertThat(BankAdmin.totalPaid()).isEqualTo(succeeded);
        assertThat(m001.get("balance").asLong()).isEqualTo(50_000_000 - succeeded);
        assertThat(m001.get("reserved").asLong()).isZero();
        assertThat(m001.get("available").asLong()).isEqualTo(m001.get("balance").asLong());
        return succeeded;
    }

    /** Hikari "connection not available" lines in a container's log mean a pool timeout happened. */
    static long poolTimeouts(String container) {
        try {
            Process p = new ProcessBuilder("docker", "logs", container).redirectErrorStream(true).start();
            long n = new String(p.getInputStream().readAllBytes()).lines()
                    .filter(l -> l.contains("Connection is not available") || l.contains("HikariPool") && l.contains("timeout")).count();
            p.waitFor();
            return n;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
