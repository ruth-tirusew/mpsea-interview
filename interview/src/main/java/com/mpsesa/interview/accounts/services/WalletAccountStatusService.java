package com.mpsesa.interview.accounts.services;

import com.mpsesa.interview.accounts.data.WalletStatusRequest;
import com.mpsesa.interview.accounts.data.WalletStatusResponse;
import com.mpsesa.interview.accounts.domain.WalletAccount;
import com.mpsesa.interview.accounts.repository.WalletAccountRepository;
import com.mpsesa.interview.core.ApiException;
import com.mpsesa.interview.core.DbLocks;
import com.mpsesa.interview.core.ErrorCode;
import com.mpsesa.interview.core.Times;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletAccountStatusService {
    private final WalletAccountRepository wallets;
    private final DbLocks locks;

    public WalletAccountStatusService(WalletAccountRepository wallets, DbLocks locks) {
        this.wallets = wallets;
        this.locks = locks;
    }

    /**
     * Takes the same row lock every debit takes, so once this commits no debit can have passed its
     * status check on the old value: in-flight debits either committed before us or see FROZEN.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public WalletStatusResponse setStatus(String walletId, WalletStatusRequest req) {
        locks.boundWaits();
        WalletAccount w = wallets.lockById(walletId)
                .orElseThrow(() -> new ApiException(ErrorCode.WALLET_NOT_FOUND, "wallet " + walletId + " not found"));
        if (w.getStatus() != req.status()) {
            w.setStatus(req.status());
        }
        return new WalletStatusResponse(w.getWalletId(), w.getStatus().name(), Times.iso(w.getUpdatedAt()));
    }
}
