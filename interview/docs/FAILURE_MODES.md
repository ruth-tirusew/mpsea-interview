<!-- Every way an API could lose money, create money, pay twice, block a thread or hang.
     One line each. Status after the colon: how it is prevented, or "not handled". -->

## Cross-cutting

1. Transaction happens at the bank and we receive no reply: payout is UNKNOWN, never FAILED; we ask the bank (GET) and resubmit if it has no record (bank is idempotent on payout_id).
2. Race conditions from client retries: Idempotency-Key claimed with INSERT .. ON CONFLICT in the same transaction as the effect.
3. Silent webhook/callback failures: callbacks are not trusted to arrive; SUBMITTED payouts are reconciled by polling the bank after 12 s.
4. Floating point: all money is `long` santim, DB columns are bigint, CHECK (balance >= 0, reserved >= 0, reserved <= balance).
5. Reconciliation/batch lag or failure: the worker is DB-driven with leases, runs on both instances, and any instance finishes a dead one's work.
6. State held in one JVM (lock, cache, queue) is invisible to the other instance: nothing money-related lives in memory; locks are Postgres row locks, queues are table rows.

## 5.1 POST /p2p/transfers

7. Debit without credit: both balance updates, the transfer row and both ledger rows commit in one transaction.
8. Two transfers overdraw a wallet: wallet rows are locked FOR UPDATE and available is checked after the lock; CHECK constraint is the backstop.
9. Opposing transfers deadlock: rows always locked in ascending wallet_id order.
10. Same Idempotency-Key twice, concurrently: second insert blocks on the unique key until the first commits, then replays the stored response; 409 REQUEST_IN_PROGRESS if the first takes over 1.5 s.
11. Same key, different body: request hash compared, 409 IDEMPOTENCY_CONFLICT.
12. Hot wallet blocks threads: lock waits bounded by `SET LOCAL lock_timeout = 1500ms`, surfaced as 503/409, never a hang.
13. Pool exhaustion by waiting transactions: transactions are short, no bank call or sleep inside them, connection-timeout 2 s maps to 503.
14. Error leaks (stack, SQL, constraint names): one ControllerAdvice, fixed messages.
15. Business error cached under a key then state changes: errors are stored too, so a replay is identical (documented decision).

## 5.2 POST /p2p/transfers/{id}/reversal

16. Reversed twice, concurrently: transfer row locked FOR UPDATE, status flips to REVERSED; `reversals.transfer_id UNIQUE` is the backstop.
17. Reversal overdraws the receiver: available checked on the debited wallet after locking it.
18. Frozen debited wallet loses money: debited wallet must be ACTIVE; credited wallet may be FROZEN.
19. Reversal racing a spend of the same money (S2.2): both take the same wallet row lock, exactly one sees enough funds.

## 5.3 GET/PUT wallets

20. Torn read of balance vs reserved: one row, one statement.
21. Debit commits after a freeze returned: freeze takes the same row lock every debit takes and every debit checks status after acquiring it.
22. Freeze starved by traffic on the wallet: lock wait bounded; transactions short; freeze answers within 2 s in S3.1.

## 5.4 POST /payouts

23. Reservation without payout row (or the reverse): both in one transaction.
24. Request thread blocked by a slow bank: the request never calls the bank; worker does it on its own bounded pool.
25. Bank paid twice: payout_id is the bank's idempotency key; resubmits are safe; a payout has a single row and lease.
26. Bank paid a payout we report FAILED: FAILED only on REJECTED, FAILED callback or FAILED status; a timeout is UNKNOWN.
27. Two instances submit the same payout: `SELECT .. FOR UPDATE SKIP LOCKED` plus a 20 s lease.
28. Instance killed mid-payout: its lease expires, the other instance resumes (queries the bank first when the state was SUBMITTING).
29. DB connection held during a bank call: none; bank calls run outside any transaction.
30. Payout stuck forever: not handled beyond retrying while the bank says IN_PROGRESS or does not answer; see DESIGN Q3.

## 5.5 POST /callbacks/bank

31. Duplicate callbacks double-release: `bank_callbacks.event_id` PK; copies serialise on the payout row lock.
32. Callback before submit returned: applied from any non-final state; the late submit response is ignored if the payout is already final.
33. Contradicting callback changes a final status: ignored, conflict=true.
34. Unknown payout: 404 and event not recorded so the retry works.
35. Slow response (>2 s): only two short statements under bounded lock waits.

## Access control (added beyond the spec)

36. Unauthenticated caller moves money or freezes a wallet: API key required; freeze and reset need the admin key (401/403).
37. Forged callback releases or debits a reservation: HMAC signature plus timestamp tolerance, bank role only.
38. Service started without credentials: it refuses to start (no default keys).
