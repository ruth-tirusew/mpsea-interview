package com.mpsesa.interview.idempotency.repository;

import com.mpsesa.interview.idempotency.domain.IdempotencyKey;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into idempotency_keys (idem_key, request_hash, state, created_at, expires_at)
            values (:key, :hash, 'IN_PROGRESS', now(), :expiresAt)
            on conflict (idem_key) do nothing
            """, nativeQuery = true)
    int claim(@Param("key") String key, @Param("hash") String hash, @Param("expiresAt") Instant expiresAt);

    @Modifying(flushAutomatically = true)
    @Query("update IdempotencyKey k set k.state = 'DONE', k.responseStatus = :status, k.responseBody = :body"
            + " where k.idemKey = :key")
    int complete(@Param("key") String key, @Param("status") int status, @Param("body") String body);

    @Modifying
    @Query("delete from IdempotencyKey k where k.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
