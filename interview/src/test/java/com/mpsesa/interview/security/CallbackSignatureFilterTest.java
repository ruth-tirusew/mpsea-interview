package com.mpsesa.interview.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class CallbackSignatureFilterTest {
    private static final String SECRET = "test-secret";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final byte[] BODY = "{\"event_id\":\"e1\"}".getBytes(StandardCharsets.UTF_8);

    private final CallbackSignatureFilter filter = new CallbackSignatureFilter(
            new SecurityProperties.Callback(SECRET, Duration.ofMinutes(5)), Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private boolean authenticated(String timestamp, String signature, byte[] body) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mpesa/v1/callbacks/bank");
        req.setContent(body);
        if (timestamp != null) req.addHeader("X-Bank-Timestamp", timestamp);
        if (signature != null) req.addHeader("X-Bank-Signature", signature);
        MockFilterChain chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        // the body must still be readable by the controller after the filter consumed it
        assertThat(chain.getRequest().getInputStream().readAllBytes()).isEqualTo(body);
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    private static String ts(long offsetSeconds) {
        return String.valueOf(NOW.getEpochSecond() + offsetSeconds);
    }

    @Test
    void validSignatureAuthenticates() throws Exception {
        String t = ts(0);
        assertThat(authenticated(t, CallbackSignature.sign(SECRET, t, BODY), BODY)).isTrue();
    }

    @Test
    void tamperedBodyIsRejected() throws Exception {
        String t = ts(0);
        String sig = CallbackSignature.sign(SECRET, t, BODY);
        assertThat(authenticated(t, sig, "{\"event_id\":\"e2\"}".getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void wrongSecretIsRejected() throws Exception {
        String t = ts(0);
        assertThat(authenticated(t, CallbackSignature.sign("other", t, BODY), BODY)).isFalse();
    }

    @Test
    void staleOrFutureTimestampIsRejected() throws Exception {
        String old = ts(-301);
        String future = ts(301);
        assertThat(authenticated(old, CallbackSignature.sign(SECRET, old, BODY), BODY)).isFalse();
        assertThat(authenticated(future, CallbackSignature.sign(SECRET, future, BODY), BODY)).isFalse();
    }

    @Test
    void missingOrMalformedHeadersAreRejected() throws Exception {
        assertThat(authenticated(null, null, BODY)).isFalse();
        assertThat(authenticated("abc", "deadbeef", BODY)).isFalse();
        assertThat(authenticated(ts(0), null, BODY)).isFalse();
    }
}
