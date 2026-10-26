package com.mpsesa.interview.ledger.repository;

import com.mpsesa.interview.ledger.domain.LedgerEntry;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only. Statements are keyset-paged on entry_id (index ledger_entries_wallet_idx), never OFFSET. */
public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {

    /** First page. Pass PageRequest.of(0, limit + 1) and use the extra row to derive next_cursor. */
    List<LedgerEntry> findByWalletIdOrderByEntryIdDesc(String walletId, Pageable page);

    /** Following pages: strictly older than the cursor. */
    List<LedgerEntry> findByWalletIdAndEntryIdLessThanOrderByEntryIdDesc(String walletId, Long cursor, Pageable page);
}
