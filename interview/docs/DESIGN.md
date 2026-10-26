# DESIGN

Issues addressed from the  faliure modes
---

 - Concurrent request with the same Idempotency-Key: Any failures and duplicate trials will be caught due to the stored keys p
 - Reversals: reversals will not be usinf idempotency keys however they rely on the current state of the transaction
 - Threads and pools follow the provided document
 - Correctness comes from explicit row locks
 - Security: apikeys/auth is  requrired for provided apis




Handling Database restart
--- 
1. the in-flight bank call finishes or times out; 
2. the write of the result fails (connection lost), the transaction rolls back, state stays SUBMITTING and reserved stays reserved; 
3. requests meanwhile get 503 SERVICE_UNAVAILABLE (pool reconnects, Hikari validates connections); 
4. the worker's poll fails and logs; 
5. when the DB is back the lease (20 s) expires and the payout is claimed again; 
6. SUBMITTING means the outcome may exist, so we ask the bank (GET) first: SUCCEEDED applies debit + release + ledger once; FAILED/REJECTED releases; IN_PROGRESS waits; NOT_FOUND resubmits (idempotent). A callback that arrived while the DB was down gets 503 and the bank retries it; a duplicate is absorbed by `event_id`. Money cannot be lost or doubled: the reservation is a committed row, and only the single locked `applyFinal` moves it.

