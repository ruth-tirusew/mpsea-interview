package com.mpsesa.interview.transfers.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReversalResponse(
        @JsonProperty("transfer_id") String transferId,
        @JsonProperty("reversal_id") String reversalId,
        String status,
        long amount) {
}
