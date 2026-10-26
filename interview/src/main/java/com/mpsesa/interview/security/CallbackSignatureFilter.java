package com.mpsesa.interview.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates bank callbacks by an HMAC over the raw body. The timestamp in the signed material bounds how long a
 * captured request can be replayed; event_id dedup in CallbackService makes any replay inside the window harmless.
 */
class CallbackSignatureFilter extends OncePerRequestFilter {
    static final String PATH_PREFIX = "/mpesa/v1/callbacks/";
    static final String TIMESTAMP_HEADER = "X-Bank-Timestamp";
    static final String SIGNATURE_HEADER = "X-Bank-Signature";
    private static final int MAX_BODY = 16 * 1024;
    private static final Logger log = LoggerFactory.getLogger(CallbackSignatureFilter.class);

    private final String secret;
    private final Duration tolerance;
    private final Clock clock;

    CallbackSignatureFilter(SecurityProperties.Callback cfg, Clock clock) {
        this.secret = cfg.secret();
        this.tolerance = cfg.tolerance() == null ? Duration.ofMinutes(5) : cfg.tolerance();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = req.getInputStream().readNBytes(MAX_BODY + 1);
        if (body.length <= MAX_BODY && valid(req, body)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "bank", null, List.of(new SimpleGrantedAuthority("ROLE_BANK"))));
        } else if (body.length > MAX_BODY) {
            log.warn("callback body too large");
        }
        chain.doFilter(new CachedBodyRequest(req, body), res);
    }

    private boolean valid(HttpServletRequest req, byte[] body) {
        String ts = req.getHeader(TIMESTAMP_HEADER);
        String sig = req.getHeader(SIGNATURE_HEADER);
        if (ts == null || sig == null) {
            log.warn("callback without signature headers");
            return false;
        }
        long sent;
        try {
            sent = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            log.warn("callback with malformed timestamp");
            return false;
        }
        if (Math.abs(clock.instant().getEpochSecond() - sent) > tolerance.toSeconds()) {
            log.warn("callback timestamp outside tolerance");
            return false;
        }
        if (!CallbackSignature.matches(secret, ts, body, sig)) {
            log.warn("callback signature mismatch");
            return false;
        }
        return true;
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener l) { throw new UnsupportedOperationException(); }
                @Override public int read() { return in.read(); }
                @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
