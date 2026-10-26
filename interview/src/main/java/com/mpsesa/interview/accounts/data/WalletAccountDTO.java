package com.mpsesa.interview.accounts.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WalletAccountDTO(
        @JsonProperty("wallet_id") String walletId,
        String status,
        long balance,
        long reserved,
        long available) {
}
