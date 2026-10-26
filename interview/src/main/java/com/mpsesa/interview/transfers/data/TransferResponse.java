package com.mpsesa.interview.transfers.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TransferResponse(
        @JsonProperty("transfer_id") String transferId,
        String status,
        @JsonProperty("from_available_after") long fromAvailableAfter,
        @JsonProperty("created_at") String createdAt) {
}
