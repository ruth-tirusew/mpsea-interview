# DESIGN


| Question | Decision | Status |
|---|---|---|
| Idempotency mechanism | Postgres `idempotency_keys`; `INSERT ... ON CONFLICT DO NOTHING` is the claim (1 row = we own the key, 0 = someone else does) | Committed |
| Concurrent same-key request | Return 409 `REQUEST_IN_PROGRESS` immediately; no thread is parked waiting. A bounded wait is a Phase 2 option | Committed (error code, claim); service logic in progress |
| Same key, different body | 409 `IDEMPOTENCY_CONFLICT`, detected by `request_hash` | Committed (column, error code) |
| Transaction isolation | `READ COMMITTED` plus explicit row locks. Locks give the correctness; higher isolation would only add serialization retries | Decided |
| Lock waits | `SET LOCAL lock_timeout = '1500ms'` via `DbLocks.boundWaits()` so a contended row fails fast instead of hanging a request | Committed |
| Ledger model | Balance-row model: `wallets.balance/reserved` are the source of truth, `ledger_entries` is an append-only audit trail written in the same tx. Event sourcing is the Phase 2 direction (see below) | Decided |

## Principles

- **The database enforces the money invariants, not only the code.** A bug in a service must not be able to create negative or double-spent money.
- **Duplicates and retries are harmless by construction.** Idempotency keys for client requests, `event_id` for bank callbacks, conditional updates for state changes.
- **No bank call, and no wait on the idempotency claim, while holding a DB connection** (pool is 10 per instance).
- **Lock order is always ascending `wallet_id`**, which rules out deadlock between opposite transfers.
- **`available` is always `balance - reserved`, computed in SQL**, never cached.
- **Every state change on `payouts` is a conditional update** (`WHERE state = ...`), so replays and races are no-ops.
- **Never guess an unknown bank result.** Resolve it or retry with the same `payout_id`; release funds only on a confirmed failure.

## Failure modes and how this design answers them

Source list: `FAILURE_MODES.md`. Each item is mapped to a mechanism and its status.

| # | Failure mode | Answer | Status |
|---|---|---|---|
| 1 | We call the third party (bank) and get no reply | The outcome is **unknown**, not failed. Funds stay reserved and the payout moves to `UNKNOWN`. We never release on a timeout. Resolution is by `GET /bank/payouts/{id}` or by resubmitting with the same `payout_id`, which the bank treats as idempotent. Funds are released only on a confirmed `FAILED`/`REJECTED`/`NOT_FOUND`. The bank is never called inside a DB tx, so a hang cannot hold connections or locks | Schema and `UNKNOWN` handling in progress |
| 2 | Race conditions from repeated client retries | Three layers. (a) Idempotency key: the `ON CONFLICT DO NOTHING` claim lets exactly one request run; a replay gets the stored response; a changed body gets 409 `IDEMPOTENCY_CONFLICT`; an in-flight duplicate gets 409 `REQUEST_IN_PROGRESS`. (b) Row locks in ascending `wallet_id` order serialise balance changes and avoid deadlock. (c) DB constraints are the last line: `UNIQUE(transfer_id)` on reversals, `CHECK` on balances | Claim, constraints, `DbLocks` committed; service flow in progress |
| 3 | Silent webhook/callback failures | A callback can be lost, duplicated, early or contradictory. Lost: the worker does not depend on callbacks; it polls the bank for payouts stuck in `UNKNOWN` or submitted past a deadline, and callbacks are only a fast path. Duplicate: `bank_callbacks.event_id` PK, checked after locking the payout. Early: the payout row exists before the bank call, so there is always something to apply to. Unknown payout: 404 so the bank retries. Contradicting final status: payout flagged `conflict = true` and not overwritten. Silence is made visible by metrics (oldest `UNKNOWN` age, conflict count) | Dedup and locking committed; polling and metrics in progress / Phase 2a |
| 4 | Floating-point differences | No floats anywhere. Amounts are `bigint` santim, conversion uses `multiplyExact`, and display formatting is integer division only. Limits are enforced in code and by `CHECK` | Committed |
| 5 | Reconciliation or batch job lags or fails | Correctness never depends on these jobs, they only detect drift. Payout work uses a lease (`lease_until`) and `FOR UPDATE SKIP LOCKED`, so a crashed worker's rows are picked up by another instance after the lease expires. Each step is a conditional update, so re-running a job is a no-op. Bulk batches keep `rows_hash` and per-row `result`, so a restarted batch resumes instead of repeating rows. The reconciliation job is read-only: it compares `SUM(balance)` with the ledger and our `SUCCEEDED` payouts with the bank's paid total, and alerts on drift instead of auto-correcting. Lag is bounded by the sweeper's backoff and max-attempts escalation to manual review | Schema committed; worker in progress; reconciliation and sweeper are Phase 2a |

Residual risk we accept in Phase 1: if the bank never answers and never has the payout, the payout stays `UNKNOWN` with funds reserved until an operator or the sweeper escalates it. That is deliberate; guessing could pay out twice or release funds that were sent.

## Phase 1: what is built

### Committed

| Area | What exists | Why it is built this way |
|---|---|---|
| Money | `Money` helpers, amounts as `bigint` santim, limits 1 to 5,000,000 | No float error; `multiplyExact` fails loudly on overflow. Limits are checked in code and again by a DB `CHECK` |
| Wallet schema | `wallets` with `CHECK (balance >= 0)`, `CHECK (reserved >= 0)`, `CHECK (reserved <= balance)`, status `ACTIVE`/`FROZEN` | Invariants live in the DB |
| Transfer and reversal schema | `transfers` (sequence ids), `reversals` with `UNIQUE(transfer_id)` | The unique key is the arbiter: a transfer can be reversed at most once. The loser gets 409 `ALREADY_REVERSED` |
| Ledger schema | `ledger_entries` (amount, `balance_after`), indexed `(wallet_id, entry_id DESC)` | Append-only audit trail with fast statement reads |
| Payout schema | `payouts` with `state`, `attempts`, `next_attempt_at`, `lease_owner`, `lease_until`, `conflict`; partial index on non-terminal rows | Supports a leased worker and keeps the poller index small |
| Bulk payout schema | `b2c_batches`, `b2c_rows` with `rows_hash` | Replayed batch is detectable |
| Wallet read path | `WalletAccount`, `WalletAccountRepository`, `WalletAccountReadService`, `WalletController` | Read-only view of a wallet |
| Error contract | `ErrorCode`, `ApiException`, `ControllerAdvice`, `ErrorResponse` | One error shape, one HTTP status per code (400, 404, 409, 422, 503, 500). No SQL or stack text leaks |
| Idempotency store | `IdempotencyKey` entity; repository with `claim`, `complete`, `deleteExpired`; states `IN_PROGRESS` / `DONE` | The PK insert is the claim, so two concurrent requests cannot both win. `DONE` stores status and body for replay |
| Callback ingestion | `BankCallback`, `BankCallbackRepository`, `CallbackController`, `CallbackService` | See flow below |
| Utilities | `DbLocks`, `Times.iso` | Bounded lock waits; UTC millisecond timestamps in API output |
| Build | Gradle dependencies, `e2e-tests` and `fake-bank` modules, Flyway `V1__schema.sql` | |

**Callback flow (`CallbackService.handle`)**

1. Bound the lock wait to 1.5 s.
2. Lock the payout row (`lockById`). Unknown payout gives 404 so the bank retries. This serialises concurrent callbacks for one payout.
3. `insertIfAbsent(event_id)`. A return of 0 means the event was already processed, so return quietly.
4. Otherwise call `PayoutService.applyFinal` once.

The payout is locked before the dedup insert, so two simultaneous duplicates cannot both apply the result.

> Note: `CallbackService` in `HEAD` references `PayoutService`, `PayoutRepository`, `Payout` and `PayoutState`, which are not committed yet. `HEAD` alone does not compile.

### In progress (working tree, uncommitted)

| Area | Mechanism |
|---|---|
| Idempotency service | `IdempotencyService` and `StoredResponse` (staged). Claim, replay stored response, conflict on hash mismatch |
| Transfer (5.1) | One tx, lock both wallet rows in ascending `wallet_id` order (`FOR UPDATE`), check `ACTIVE` and `available` after the lock |
| Reversal (5.2) | Same lock order; `UNIQUE(transfer_id)` decides the winner; the credited side may be `FROZEN` |
| Wallet freeze (5.3) | `WalletAccountStatusService`, `WalletStatusRequest/Response`. Takes the same row lock, so any later debit sees `FROZEN` |
| Ledger | `ledger/` package: entries written in the same tx as the balance change |
| Payout (5.4) | Tx 1 reserves funds and inserts the payout as `RESERVED`; returns 202 within 500 ms. The bank is never called inside a DB tx |
| Payout worker | `@Scheduled` poller claims rows with `FOR UPDATE SKIP LOCKED` and `lease_until`. A dead instance's lease expires and another takes over |
| Unknown bank result | Timeout or 504 leaves the payout `UNKNOWN`. Resolve via `GET /bank/payouts/{id}` or resubmit with the same `payout_id` (bank is idempotent) |
| Bank client and fake bank | `bank/` package; `fake-bank` runs separately on 9090 with configurable failure rates |
| Admin / reset | `admin/` package, `/internal/reset` for tests |
| Tests | `e2e-tests` for scenarios S1.1 to S5.2, run through docker-compose against both instances, using latches or `CompletableFuture` instead of `Thread.sleep` |

### Planned

- Mark every line in `FAILURE_MODES.md` as prevented (with how) or explicitly "not handled".
- Commit the untracked packages in small steps so `HEAD` compiles at every commit.

## Phase 2

### 2a. Hardening
- **Reconciliation job**: compare `SUM(balance)` with the ledger, and our `SUCCEEDED` payouts with the bank's paid total. Alert on drift; do not silently fix it.
- **Stuck-payout sweeper** with exponential backoff and escalation to a manual-review state after max attempts.
- **Bounded wait** on an in-flight idempotency key instead of an immediate 409.
- **Idempotency expiry** job (24 h), using the existing `deleteExpired`.
- **Metrics**: pool usage, payout counts by state, age of the oldest `UNKNOWN`, callback conflicts.

### 2b. Event sourcing (possible next iteration)

#### Why it fits this system

The current model already behaves like a partial event log: `ledger_entries` is append-only, reversals are compensations rather than edits, and bank callbacks are stored by `event_id`. The remaining problem is that balances are mutable state kept beside the log, so the two can drift, and every guarantee depends on holding a row lock. Making the log the source of truth removes that duplication.

Benefits specific to this domain:

- **One truth, no drift.** Balance is derived from events, so "does the ledger match the balance?" stops being a reconciliation job and becomes a replay check.
- **Audit and time travel.** Rebuild any wallet at any past point, which is what disputes and regulators ask for.
- **Payouts become explicit workflows.** Each step (reserved, submitted, bank unknown, settled, released) is an event. After a crash, recovery is replay plus resume from the last event, rather than inferring intent from a `state` column and a lease.
- **Callbacks get a full record.** Out-of-order and contradicting bank callbacks are kept as raw events instead of only setting `conflict = true`.
- **Reversal is already a compensating event**, so the model matches today's behaviour.
- **Cheaper concurrency control under low contention** (optimistic append instead of `FOR UPDATE`).

#### Sketch

| Piece | Design |
|---|---|
| Event store | `events(stream_id, version, type, payload, created_at)` with `UNIQUE(stream_id, version)`; one stream per wallet |
| Optimistic concurrency | Writer appends at `expected_version`; a unique violation means retry. Replaces `FOR UPDATE`. Worse on hot wallets (for example M001), so keep the lock path for those or batch writes |
| Events | `WalletCredited`, `WalletDebited`, `FundsReserved`, `ReservationReleased`, `PayoutSettled`, `WalletFrozen`, `TransferReversed` |
| Projections (CQRS) | `wallets` becomes a projection of balance, reserved and status. Update it in the same tx (strongly consistent) or with an async projector. Wallet read (5.3) must stay consistent, so the same-tx projection is the safe default |
| Snapshots | Store balance at version N so replaying a long stream (1M entries, S7.2) stays cheap |
| Payout saga | `PayoutRequested → FundsReserved → SubmittedToBank → (BankAccepted / BankUnknown) → Settled / Released` |
| Transactional outbox | Events and the "call the bank" intent are written in one tx; a relay publishes them. At-least-once delivery with exactly-once effect, no broker required |
| Callbacks as events | `BankCallbackReceived` stored raw (deduped by `event_id`), then applied by a handler |
| Replay / audit | Rebuild any wallet at any time and re-derive balances to verify the projection |
