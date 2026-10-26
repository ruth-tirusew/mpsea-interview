

1. Transaction happens at the bank and we receive no reply: payout is UNKNOWN, never FAILED; we ask the bank (GET) and resubmit if it has no record (bank is idempotent on payout_id).
2. Race conditions from client retries: Idempotency-Key claimed with INSERT .. ON CONFLICT in the same transaction as the effect.
3. Silent webhook/callback failures: callbacks are not trusted to arrive; SUBMITTED payouts are reconciled by polling the bank after 12 s.
4. Floating point: all money is `long` santim, DB columns are bigint, CHECK (balance >= 0, reserved >= 0, reserved <= balance).
5. Reconciliation/batch lag or failure: the worker is DB-driven with leases, runs on both instances, and any instance finishes a dead one's work.
6. State held in one JVM (lock, cache, queue) is invisible to the other instance: nothing money-related lives in memory; locks are Postgres row locks, queues are table rows.
7. Debit without credit: both balance updates, the transfer row and both ledger rows commit in one transaction.
8. Two transfers overdraw a wallet: wallet rows are locked FOR UPDATE and available is checked after the lock; CHECK constraint is the backstop.
9. Opposing transfers deadlock: rows always locked in ascending wallet_id order.
10. Same Idempotency-Key twice, concurrently: second insert blocks on the unique key until the first commits, then replays the stored response; 409 REQUEST_IN_PROGRESS if the first takes over 1.5 s.
