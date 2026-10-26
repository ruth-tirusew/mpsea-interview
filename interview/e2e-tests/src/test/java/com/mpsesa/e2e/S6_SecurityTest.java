package com.mpsesa.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class S6_SecurityTest {
    private static final Map<String, Object> EVENT =
            Map.of("event_id", "sec-1", "payout_id", "P-none", "bank_ref", "BR", "status", "SUCCEEDED");

    @BeforeEach
    void reset() {
        Api.resetAll();
    }

    @Test
    void user_endpoints_need_a_valid_api_key() {
        String b = Api.instance(0);
        assertThat(Api.withKey(b, "GET", "/mpesa/v1/wallets/W001", null, null).status()).isEqualTo(401);
        Api.Resp bad = Api.withKey(b, "GET", "/mpesa/v1/wallets/W001", "nope", null);
        assertThat(bad.status()).isEqualTo(401);
        assertThat(bad.error()).isEqualTo("UNAUTHORIZED");
        assertThat(Api.withKey(b, "GET", "/mpesa/v1/wallets/W001", Api.USER_KEY, null).status()).isEqualTo(200);
    }

    @Test
    void admin_surface_rejects_regular_user_keys() {
        String b = Api.instance(1);
        assertThat(Api.withKey(b, "POST", "/mpesa/internal/reset", Api.USER_KEY, null).status()).isEqualTo(403);
        Api.Resp freeze = Api.withKey(b, "PUT", "/mpesa/v1/wallets/W010/status", Api.USER_KEY, Map.of("status", "FROZEN"));
        assertThat(freeze.status()).isEqualTo(403);
        assertThat(freeze.error()).isEqualTo("FORBIDDEN");
        assertThat(Api.balance("W010")).isEqualTo(1_000_000);
        assertThat(Api.withKey(b, "PUT", "/mpesa/v1/wallets/W010/status", Api.ADMIN_KEY, Map.of("status", "ACTIVE")).status())
                .isEqualTo(200);
    }

    @Test
    void callbacks_require_a_valid_bank_signature() {
        String b = Api.instance(0);
        long now = System.currentTimeMillis() / 1000;
        assertThat(Api.rawCallback(b, Map.of(), "{}").status()).isEqualTo(401);
        assertThat(Api.signedCallback(b, EVENT, "wrong-secret", now).status()).isEqualTo(401);
        assertThat(Api.signedCallback(b, EVENT, Api.CALLBACK_SECRET, now - 3600).status()).isEqualTo(401);
        // an API key is not a bank credential, and a user key must not reach the callback endpoint
        assertThat(Api.withKey(b, "POST", "/mpesa/v1/callbacks/bank", Api.ADMIN_KEY, EVENT).status()).isEqualTo(401);
        // a correct signature gets through to the handler (unknown payout, so 404 rather than 401)
        assertThat(Api.callback(b, EVENT).status()).isEqualTo(404);
    }

    @Test
    void a_signature_does_not_cover_a_different_body() {
        String b = Api.instance(1);
        long now = System.currentTimeMillis() / 1000;
        Api.Resp signedForOther = Api.rawCallback(b,
                Map.of("X-Bank-Timestamp", String.valueOf(now), "X-Bank-Signature", "0".repeat(64)),
                "{\"event_id\":\"x\",\"payout_id\":\"p\",\"status\":\"SUCCEEDED\"}");
        assertThat(signedForOther.status()).isEqualTo(401);
    }
}
