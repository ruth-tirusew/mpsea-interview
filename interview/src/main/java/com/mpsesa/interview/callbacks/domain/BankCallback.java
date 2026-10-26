package com.mpsesa.interview.callbacks.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bank_callbacks")
public class BankCallback {
    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "payout_id") private String payoutId;
    private String status;
    @Column(name = "received_at") private Instant receivedAt = Instant.now();

    protected BankCallback() {
    }

    public BankCallback(String eventId, String payoutId, String status) {
        this.eventId = eventId;
        this.payoutId = payoutId;
        this.status = status;
    }

    public String getEventId() { return eventId; }
    public String getPayoutId() { return payoutId; }
    public String getStatus() { return status; }
    public Instant getReceivedAt() { return receivedAt; }
}
