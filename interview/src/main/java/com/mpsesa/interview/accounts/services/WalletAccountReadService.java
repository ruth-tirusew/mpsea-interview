package com.mpsesa.interview.accounts.services;

import com.mpsesa.interview.accounts.data.WalletAccountDTO;
import com.mpsesa.interview.accounts.domain.WalletAccount;
import com.mpsesa.interview.accounts.repository.WalletAccountRepository;
import com.mpsesa.interview.core.ApiException;
import com.mpsesa.interview.core.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletAccountReadService {
    private final WalletAccountRepository wallets;

    public WalletAccountReadService(WalletAccountRepository wallets) {
        this.wallets = wallets;
    }

    @Transactional(readOnly = true)
    public WalletAccountDTO get(String walletId) {
        WalletAccount w = wallets.findById(walletId)
                .orElseThrow(() -> new ApiException(ErrorCode.WALLET_NOT_FOUND, "wallet " + walletId + " not found"));
        return new WalletAccountDTO(w.getWalletId(), w.getStatus().name(), w.getBalance(), w.getReserved(), w.available());
    }
}
