package com.mpsesa.interview.ledger.data;

public record LedgerPosting(String walletId, String type, long amount, String reference) {
}
