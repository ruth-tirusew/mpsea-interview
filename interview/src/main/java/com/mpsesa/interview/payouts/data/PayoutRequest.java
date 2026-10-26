package com.mpsesa.interview.payouts.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mpsesa.interview.core.Money;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PayoutRequest(
        @JsonProperty("wallet_id") @NotBlank @Size(max = 16) String walletId,
        @JsonProperty("bank_account") @NotBlank @Size(max = 64) String bankAccount,
        @NotNull @Min(Money.MIN_AMOUNT) @Max(Money.MAX_AMOUNT) Long amount) {
}
