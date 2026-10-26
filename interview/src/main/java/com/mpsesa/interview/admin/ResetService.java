package com.mpsesa.interview.admin;

import com.mpsesa.interview.core.Money;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResetService {
    private final JdbcTemplate jdbc;

    public ResetService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void reset() {
        jdbc.execute("TRUNCATE b2c_rows, b2c_batches, idempotency_keys, bank_callbacks, payouts,"
                + " ledger_entries, reversals, transfers, wallets RESTART IDENTITY");
        jdbc.execute("ALTER SEQUENCE transfer_seq RESTART WITH 10000");
        List<Object[]> rows = new ArrayList<>();
        for (int i = 1; i <= 105; i++) {
            rows.add(new Object[]{String.format("W%03d", i), i <= 100 ? "ACTIVE" : "FROZEN", Money.SEED_WALLET_BALANCE});
        }
        rows.add(new Object[]{"M001", "ACTIVE", Money.SEED_MERCHANT_BALANCE});
        jdbc.batchUpdate("INSERT INTO wallets (wallet_id, status, balance) VALUES (?, ?, ?)", rows);
    }
}
