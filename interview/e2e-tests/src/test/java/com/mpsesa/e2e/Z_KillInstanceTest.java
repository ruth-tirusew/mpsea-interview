package com.mpsesa.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Named Z_ so it runs last: it kills instance 1 for good, which would break any test after it. */
class Z_KillInstanceTest {
    @BeforeEach
    void reset() {
        Api.resetAll();
    }

    @Test
    void s4_3_killed_instance_never_restarts_and_every_payout_still_ends_final_and_reconciled() throws Exception {
        BankAdmin.configure(Map.of("noResponseRate", 0.5, "rejectRate", 0.1));
        List<String> ids = Payouts.createMany(30, 5_000, new ArrayList<>());

        Awaitility.await().pollDelay(Duration.ofSeconds(10)).atMost(Duration.ofSeconds(11)).until(() -> true);
        Process kill = new ProcessBuilder("docker", "kill", "-s", "KILL", "e2e-wallet-1").inheritIO().start();
        assertThat(kill.waitFor()).isZero();

        String survivor = Api.INSTANCES.get(1);
        Payouts.awaitFinal(ids, Duration.ofSeconds(120), List.of(survivor));
        Payouts.assertReconciled(ids, survivor, 5_000);
    }
}
