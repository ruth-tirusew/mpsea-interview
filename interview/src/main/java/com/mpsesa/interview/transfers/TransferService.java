package com.mpsesa.interview.transfers;

import com.mpsesa.interview.accounts.domain.WalletAccount;
import com.mpsesa.interview.accounts.domain.WalletAccountStatusEnum;
import com.mpsesa.interview.accounts.repository.WalletAccountRepository;
import com.mpsesa.interview.core.ApiException;
import com.mpsesa.interview.core.DbLocks;
import com.mpsesa.interview.core.ErrorCode;
import com.mpsesa.interview.core.Times;
import com.mpsesa.interview.ledger.domain.LedgerEntry;
import com.mpsesa.interview.ledger.repository.LedgerRepository;
import com.mpsesa.interview.transfers.data.ReversalResponse;
import com.mpsesa.interview.transfers.data.TransferRequest;
import com.mpsesa.interview.transfers.data.TransferResponse;
import com.mpsesa.interview.transfers.domain.Reversal;
import com.mpsesa.interview.transfers.domain.Transfer;
import com.mpsesa.interview.transfers.domain.TransferStatus;
import com.mpsesa.interview.transfers.repository.ReversalRepository;
import com.mpsesa.interview.transfers.repository.TransferRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One transaction per operation at READ COMMITTED. Correctness comes from row locks, not isolation:
 * wallet rows are locked FOR UPDATE in ascending id order (no deadlocks), every check runs after the
 * lock is held, and balances move with the ledger rows in the same commit (all or nothing).
 */
@Service
public class TransferService {
    private final WalletAccountRepository wallets;
    private final TransferRepository transfers;
    private final ReversalRepository reversals;
    private final LedgerRepository ledger;
    private final DbLocks locks;

    public TransferService(WalletAccountRepository wallets, TransferRepository transfers, ReversalRepository reversals,
                           LedgerRepository ledger, DbLocks locks) {
        this.wallets = wallets;
        this.transfers = transfers;
        this.reversals = reversals;
        this.ledger = ledger;
        this.locks = locks;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TransferResponse transfer(TransferRequest req) {
        locks.boundWaits();
        long amount = req.amount();
        if (req.fromWallet().equals(req.toWallet())) {
            throw new ApiException(ErrorCode.SAME_WALLET, "from_wallet and to_wallet are the same");
        }
        Map<String, WalletAccount> w = lock(req.fromWallet(), req.toWallet());
        WalletAccount from = w.get(req.fromWallet());
        WalletAccount to = w.get(req.toWallet());
        if (from.getStatus() != WalletAccountStatusEnum.ACTIVE || to.getStatus() != WalletAccountStatusEnum.ACTIVE) {
            throw new ApiException(ErrorCode.WALLET_NOT_ACTIVE, "both wallets must be ACTIVE");
        }
        requireFunds(from, amount);

        from.debit(amount);
        to.credit(amount);
        Transfer t = transfers.saveAndFlush(new Transfer(from.getWalletId(), to.getWalletId(), amount, req.reference()));
        ledger.save(new LedgerEntry(from.getWalletId(), "TRANSFER_OUT", -amount, from.getBalance(), t.publicId()));
        ledger.save(new LedgerEntry(to.getWalletId(), "TRANSFER_IN", amount, to.getBalance(), t.publicId()));
        return new TransferResponse(t.publicId(), t.getStatus().name(), from.available(), Times.iso(t.getCreatedAt()));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ReversalResponse reverse(String publicId, String reason) {
        locks.boundWaits();
        long id = parseId(publicId);
        // The transfer row lock serialises concurrent reversals; reversals.transfer_id UNIQUE is the backstop.
        Transfer t = transfers.lockById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSFER_NOT_FOUND, "transfer " + publicId + " not found"));
        if (t.getStatus() == TransferStatus.REVERSED) {
            throw new ApiException(ErrorCode.ALREADY_REVERSED, "transfer " + publicId + " is already reversed");
        }
        if (t.getStatus() != TransferStatus.COMPLETED) {
            throw new ApiException(ErrorCode.NOT_REVERSIBLE, "transfer " + publicId + " is not COMPLETED");
        }
        Map<String, WalletAccount> w = lock(t.getFromWallet(), t.getToWallet());
        WalletAccount debited = w.get(t.getToWallet());
        WalletAccount credited = w.get(t.getFromWallet());
        if (debited.getStatus() != WalletAccountStatusEnum.ACTIVE) {
            throw new ApiException(ErrorCode.WALLET_NOT_ACTIVE, debited.getWalletId() + " must be ACTIVE");
        }
        requireFunds(debited, t.getAmount());

        debited.debit(t.getAmount());
        credited.credit(t.getAmount()); // money returning is accepted even when the wallet is FROZEN
        t.markReversed();
        Reversal r = reversals.saveAndFlush(new Reversal(t.getTransferId(), reason));
        ledger.save(new LedgerEntry(debited.getWalletId(), "REVERSAL_OUT", -t.getAmount(), debited.getBalance(), r.publicId()));
        ledger.save(new LedgerEntry(credited.getWalletId(), "REVERSAL_IN", t.getAmount(), credited.getBalance(), r.publicId()));
        return new ReversalResponse(t.publicId(), r.publicId(), "REVERSED", t.getAmount());
    }

    private Map<String, WalletAccount> lock(String a, String b) {
        Map<String, WalletAccount> found = wallets.lockAllById(List.of(a, b)).stream()
                .collect(Collectors.toMap(WalletAccount::getWalletId, Function.identity()));
        for (String id : List.of(a, b)) {
            if (!found.containsKey(id)) {
                throw new ApiException(ErrorCode.WALLET_NOT_FOUND, "wallet " + id + " not found");
            }
        }
        return found;
    }

    private static void requireFunds(WalletAccount w, long amount) {
        if (w.available() < amount) {
            throw new ApiException(ErrorCode.INSUFFICIENT_FUNDS,
                    w.getWalletId() + " available " + w.available() + ", requested " + amount);
        }
    }

    private static long parseId(String publicId) {
        try {
            if (publicId.startsWith("T-")) {
                return Long.parseLong(publicId.substring(2));
            }
        } catch (NumberFormatException ignored) {
            // falls through
        }
        throw new ApiException(ErrorCode.TRANSFER_NOT_FOUND, "transfer " + publicId + " not found");
    }
}
