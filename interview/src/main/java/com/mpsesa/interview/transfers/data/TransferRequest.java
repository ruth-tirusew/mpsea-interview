package com.mpsesa.interview.transfers.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mpsesa.interview.core.Money;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferRequest(
        @JsonProperty("from_wallet") @NotBlank @Size(max = 16) String fromWallet,
        @JsonProperty("to_wallet") @NotBlank @Size(max = 16) String toWallet,
        @NotNull @Min(Money.MIN_AMOUNT) @Max(Money.MAX_AMOUNT) Long amount,
        @NotBlank @Size(max = 64) String reference) {
}
