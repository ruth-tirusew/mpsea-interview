package com.mpsesa.interview.payouts.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PayoutAccepted(@JsonProperty("payout_id") String payoutId, String status) {
}
