package com.mpsesa.interview.accounts.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WalletStatusResponse(
        @JsonProperty("wallet_id") String walletId,
        String status,
        @JsonProperty("changed_at") String changedAt) {
}
