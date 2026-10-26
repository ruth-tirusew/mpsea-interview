package com.mpsesa.interview.idempotency.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String DONE = "DONE";

    @Id
    @Column(name = "idem_key")
    private String idemKey;

    @Column(name = "request_hash") private String requestHash;
    private String state;
    @Column(name = "response_status") private Integer responseStatus;
    @Column(name = "response_body") private String responseBody;
    @Column(name = "created_at") private Instant createdAt = Instant.now();
    @Column(name = "expires_at") private Instant expiresAt;

    protected IdempotencyKey() {
    }

    public String getIdemKey() { return idemKey; }
    public String getRequestHash() { return requestHash; }
    public String getState() { return state; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isDone() { return DONE.equals(state); }
}
