package com.mpsesa.interview.payouts.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payouts")
public class Payout {
    @Id
    @Column(name = "payout_id")
    private String payoutId;

    @Column(name = "wallet_id") private String walletId;
    @Column(name = "bank_account") private String bankAccount;
    private long amount;
    @Enumerated(EnumType.STRING) private PayoutState state = PayoutState.RESERVED;
    @Column(name = "bank_ref") private String bankRef;
    private boolean conflict;
    private int attempts;
    @Column(name = "next_attempt_at") private Instant nextAttemptAt = Instant.now();
    @Column(name = "lease_owner") private String leaseOwner;
    @Column(name = "lease_until") private Instant leaseUntil;
    @Column(name = "created_at") private Instant createdAt = Instant.now();
    @Column(name = "updated_at") private Instant updatedAt = Instant.now();

    protected Payout() {
    }

    public Payout(String payoutId, String walletId, String bankAccount, long amount) {
        this.payoutId = payoutId;
        this.walletId = walletId;
        this.bankAccount = bankAccount;
        this.amount = amount;
    }

    public String getPayoutId() { return payoutId; }
    public String getWalletId() { return walletId; }
    public String getBankAccount() { return bankAccount; }
    public long getAmount() { return amount; }
    public PayoutState getState() { return state; }
    public String getBankRef() { return bankRef; }
    public boolean isConflict() { return conflict; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLeaseOwner() { return leaseOwner; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setState(PayoutState state) { this.state = state; touch(); }
    public void setBankRef(String bankRef) { this.bankRef = bankRef; touch(); }
    public void markConflict() { this.conflict = true; touch(); }
    public void recordAttempt(Instant nextAttemptAt) { this.attempts++; this.nextAttemptAt = nextAttemptAt; touch(); }
    public void lease(String owner, Instant until) { this.leaseOwner = owner; this.leaseUntil = until; touch(); }
    public void releaseLease() { this.leaseOwner = null; this.leaseUntil = null; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
