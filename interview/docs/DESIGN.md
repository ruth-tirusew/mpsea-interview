# DESIGN

## Decisions the spec left open
- **Concurrent request with the same Idempotency-Key** (III): the duplicate waits (blocked on the unique index, max 1.5 s), then returns the stored response; if the original is still running after 1.5 s it returns **409 REQUEST_IN_PROGRESS**.
- **Business errors are stored under the key too**, so replays are byte-identical. Unexpected failures roll back and store nothing.
- **Reversal** needs no Idempotency-Key (the spec doesn't ask); it is safe to repeat anyway (409 ALREADY_REVERSED).
- **Isolation level:** READ COMMITTED. Correctness comes from explicit row locks (`FOR UPDATE`, ascending id order) and unique constraints, not from serialisable retries.
- **Idempotency mechanism:** Postgres table `idempotency_keys`; `INSERT .. ON CONFLICT DO NOTHING` inside the business transaction, response stored in the same commit, 24 h retention, hourly purge.
- **Payout internal states:** RESERVED, SUBMITTING, SUBMITTED, UNKNOWN, SUCCEEDED, FAILED. Public: PENDING / SUCCEEDED / FAILED.
- **Bank NOT_FOUND on a status query** means "resubmit", never "failed" (the bank dedupes on payout_id).
- **Fake bank "accept rate"** is the remainder after reject and no-response rates.
- Lock waits are capped by `SET LOCAL lock_timeout=1500ms` because Postgres ignores Hibernate's lock-timeout hint.
- **Authentication (beyond the spec):** Spring Security, stateless. `X-API-Key` for every API, an admin key for freeze/unfreeze and `/mpesa/internal/**`, and an HMAC signature on bank callbacks (the fake bank signs them). Keys come from env vars with no defaults. This is an addition the paper does not require; the e2e stack supplies the keys. Raise with the interviewer whether the real bank can sign.
- Optional APIs 5.6 and 5.7 are not built (see Q3).

## Q1 Every wait
1. **Tomcat threads** (50/instance): excess requests queue in the connector; each request is bounded by the items below.
2. **Hikari pool** (10/instance): max 2 s (`connection-timeout`), then 503.
3. **Wallet / transfer / payout row locks**: max 1.5 s (`lock_timeout`), then 503 (or 409 for idempotency).
4. **Idempotency key insert** blocked by an in-flight twin: max 1.5 s, then 409 REQUEST_IN_PROGRESS.
5. **Bank calls** (worker pool of 32 threads/instance, no DB connection held): hard 8 s client timeout, never on a request thread.
6. **Worker semaphore**: the poll loop only claims as many rows as there are free worker slots, so it never blocks.
7. **Callback bank retries** are the bank's: our handler does two short statements, bounded by 3.
8. **Fake bank** hang is 60 s by design, but only the caller's own 8 s timeout waits on it.
No `Thread.sleep` in the service; polling uses `@Scheduled(fixedDelay)`.

## Q2 Database restart
Between reservation and the bank's answer the payout row (state RESERVED/SUBMITTING) and the reservation were committed together, so they survive the restart. Steps: (1) the in-flight bank call finishes or times out; (2) our write of the result fails (connection lost), the transaction rolls back, state stays SUBMITTING and reserved stays reserved; (3) requests meanwhile get 503 SERVICE_UNAVAILABLE (pool reconnects, Hikari validates connections); (4) the worker's poll fails and logs; (5) when the DB is back the lease (20 s) expires and the payout is claimed again; (6) SUBMITTING means the outcome may exist, so we ask the bank (GET) first: SUCCEEDED applies debit + release + ledger once; FAILED/REJECTED releases; IN_PROGRESS waits; NOT_FOUND resubmits (idempotent). A callback that arrived while the DB was down gets 503 and the bank retries it; a duplicate is absorbed by `event_id`. Money cannot be lost or doubled: the reservation is a committed row, and only the single locked `applyFinal` moves it.

## Q3 What I left out
- **5.6 B2C disbursements**: accept the batch (rows + hash in tables, 202), then a DB-leased worker applies rows in order in chunked transactions (one `lockById` on the source wallet per chunk, ~200 rows) so 5.1 on the source wallet is never blocked longer than one chunk. Hard parts: ordered application with duplicate row_id detection, the `batch_id` replay across instances (409 on different rows via a stored hash), and the 60 s budget for 5,000 rows while keeping 5.1 p95 under 300 ms on the same hot source wallet.
- **5.7 Statement**: keyset pagination on `ledger_entries(wallet_id, entry_id DESC)` (index exists), cursor = last entry_id. Hard parts: `bigserial` ids can commit out of order under concurrency, so "every entry that existed at first page" needs a high-water mark in the cursor (a snapshot id taken from `pg_current_snapshot()`/max committed id), and the balance chain needs entries inserted under the wallet lock so order matches commit order (it does, as inserts happen inside the locked section).
- **Tests S6.x and S7.x** follow 5.6/5.7.
- **Payout finality at 2 min**: if the bank stays IN_PROGRESS or silent for the whole window the payout stays PENDING (money stays reserved). I chose safety over a guess; a production system would raise an alert at 110 s.
