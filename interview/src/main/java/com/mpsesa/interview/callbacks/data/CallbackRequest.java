package com.mpsesa.interview.callbacks.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CallbackRequest(
        @JsonProperty("event_id") @NotBlank @Size(max = 64) String eventId,
        @JsonProperty("payout_id") @NotBlank @Size(max = 32) String payoutId,
        @JsonProperty("bank_ref") @Size(max = 64) String bankRef,
        @NotNull @Pattern(regexp = "SUCCEEDED|FAILED") String status) {
}
