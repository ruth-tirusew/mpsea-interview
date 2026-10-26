package com.mpsesa.interview.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpsesa.interview.core.ApiException;
import com.mpsesa.interview.core.DbLocks;
import com.mpsesa.interview.core.ErrorCode;
import com.mpsesa.interview.core.ErrorResponse;
import com.mpsesa.interview.core.WalletProperties;
import com.mpsesa.interview.idempotency.domain.IdempotencyKey;
import com.mpsesa.interview.idempotency.repository.IdempotencyKeyRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IdempotencyService {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    public static final int MAX_KEY_LENGTH = 64;

    public record Result(int status, Object body) {
    }

    private final IdempotencyKeyRepository repo;
    private final TransactionTemplate tx;
    private final DbLocks locks;
    private final ObjectMapper json;
    private final WalletProperties props;
    private final Clock clock;

    public IdempotencyService(IdempotencyKeyRepository repo, TransactionTemplate tx, DbLocks locks,
                              ObjectMapper json, WalletProperties props, Clock clock) {
        this.repo = repo;
        this.tx = tx;
        this.locks = locks;
        this.json = json;
        this.props = props;
        this.clock = clock;
    }

    public StoredResponse execute(String key, String path, Object request, Supplier<Result> action) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key is required, max 64 characters");
        }
        String hash = hash(path + "|" + write(request));
        StoredResponse out = tx.execute(status -> {
            locks.boundWaits();
            int claimed;
            try {
                claimed = repo.claim(key, hash, Instant.now(clock).plus(props.idempotency().retention()));
            } catch (PessimisticLockingFailureException e) {
                throw new ApiException(ErrorCode.REQUEST_IN_PROGRESS, "original request with this key is still running");
            }
            if (claimed == 0) {
                IdempotencyKey seen = repo.findById(key).orElseThrow(
                        () -> new ApiException(ErrorCode.REQUEST_IN_PROGRESS, "original request with this key is still running"));
                if (!seen.getRequestHash().equals(hash)) {
                    throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "key was used with a different request");
                }
                if (!seen.isDone()) {
                    throw new ApiException(ErrorCode.REQUEST_IN_PROGRESS, "original request with this key is still running");
                }
                return new StoredResponse(seen.getResponseStatus(), seen.getResponseBody());
            }
            StoredResponse r;
            try {
                Result res = action.get();
                r = new StoredResponse(res.status(), write(res.body()));
            } catch (ApiException e) {
                r = new StoredResponse(e.code().status().value(), write(new ErrorResponse(e.code().name(), e.getMessage())));
            }
            repo.complete(key, r.status(), r.body());
            return r;
        });
        return out;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    void purgeExpired() {
        try {
            int n = tx.execute(s -> repo.deleteExpired(Instant.now(clock)));
            if (n > 0) {
                log.info("purged {} expired idempotency keys", n);
            }
        } catch (RuntimeException e) {
            log.warn("idempotency purge failed: {}", e.getClass().getSimpleName());
        }
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hash(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
