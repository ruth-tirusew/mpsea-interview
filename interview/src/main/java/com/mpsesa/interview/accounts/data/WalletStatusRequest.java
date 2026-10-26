package com.mpsesa.interview.accounts.data;

import com.mpsesa.interview.accounts.domain.WalletAccountStatusEnum;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WalletStatusRequest(@NotNull WalletAccountStatusEnum status, @Size(max = 255) String reason) {
}
