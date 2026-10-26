package com.mpsesa.interview.transfers.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "transfers")
public class Transfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // column default nextval(...): one round trip
        @Column(name = "transfer_id")
    private Long transferId;

    @Column(name = "from_wallet") private String fromWallet;
    @Column(name = "to_wallet") private String toWallet;
    private long amount;
    private String reference;
    @Enumerated(EnumType.STRING) private TransferStatus status = TransferStatus.COMPLETED;
    @Column(name = "created_at") private Instant createdAt = Instant.now();

    protected Transfer() {
    }

    public Transfer(String fromWallet, String toWallet, long amount, String reference) {
        this.fromWallet = fromWallet;
        this.toWallet = toWallet;
        this.amount = amount;
        this.reference = reference;
    }

    public Long getTransferId() { return transferId; }
    public String publicId() { return "T-" + transferId; }
    public String getFromWallet() { return fromWallet; }
    public String getToWallet() { return toWallet; }
    public long getAmount() { return amount; }
    public String getReference() { return reference; }
    public TransferStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void markReversed() { this.status = TransferStatus.REVERSED; }
}
