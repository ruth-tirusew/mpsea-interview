package com.mpsesa.interview.accounts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "wallets")
public class WalletAccount {
    @Id
    @Column(name = "wallet_id")
    private String walletId;

    @Enumerated(EnumType.STRING)
    private WalletAccountStatusEnum status;

    private long balance;
    private long reserved;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    protected WalletAccount() {
    }

    public String getWalletId() { return walletId; }
    public WalletAccountStatusEnum getStatus() { return status; }
    public void setStatus(WalletAccountStatusEnum status) { this.status = status; this.updatedAt = Instant.now(); }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getBalance() { return balance; }
    public long getReserved() { return reserved; }
    public long available() { return balance - reserved; }

    public void debit(long amount) { balance -= amount; }
    public void credit(long amount) { balance += amount; }
    public void reserve(long amount) { reserved += amount; }
    public void release(long amount) { reserved -= amount; }
}
