package com.mpsesa.interview.transfers.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** transfer_id is UNIQUE: the database is the final arbiter of "reverse once". */
@Entity
@Table(name = "reversals")
public class Reversal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // column default nextval(...): one round trip
        @Column(name = "reversal_id")
    private Long reversalId;

    @Column(name = "transfer_id") private Long transferId;
    private String reason;
    @Column(name = "created_at") private Instant createdAt = Instant.now();

    protected Reversal() {
    }

    public Reversal(Long transferId, String reason) {
        this.transferId = transferId;
        this.reason = reason;
    }

    public Long getReversalId() { return reversalId; }
    public String publicId() { return "R-" + reversalId; }
    public Long getTransferId() { return transferId; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
