package com.mpsesa.interview.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id")
    private Long entryId;

    @Column(name = "wallet_id")
    private String walletId;
    private String type;
    private long amount;
    @Column(name = "balance_after")
    private long balanceAfter;
    private String reference;
    @Column(name = "created_at")
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(String walletId, String type, long amount, long balanceAfter, String reference) {
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.reference = reference;
        this.createdAt = Instant.now();
    }

    public Long getEntryId() { return entryId; }
    public String getWalletId() { return walletId; }
    public String getType() { return type; }
    public long getAmount() { return amount; }
    public long getBalanceAfter() { return balanceAfter; }
    public String getReference() { return reference; }
    public Instant getCreatedAt() { return createdAt; }
}
