package com.mpsesa.interview.payouts.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PayoutView(
        @JsonProperty("payout_id") String payoutId,
        @JsonProperty("wallet_id") String walletId,
        long amount,
        String status,
        @JsonProperty("bank_ref") String bankRef,
        boolean conflict,
        @JsonProperty("updated_at") String updatedAt) {
}
