package com.mpsesa.interview.payouts.repository;

import com.mpsesa.interview.payouts.domain.Payout;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface PayoutRepository extends JpaRepository<Payout, String> {

    /** Serialises callback application against the worker for one payout. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "1500"))
    @Query("select p from Payout p where p.payoutId = :id")
    Optional<Payout> lockById(@Param("id") String id);

    /**
     * Work claim for the worker: non-final payouts that are due and whose lease is free or
     * expired (a dead instance's lease simply runs out). SKIP LOCKED keeps instances from
     * blocking each other. JPQL cannot express it, hence native.
     */
    @Query(value = """
            select * from payouts
            where state not in ('SUCCEEDED', 'FAILED')
              and next_attempt_at <= :now
              and (lease_until is null or lease_until < :now)
            order by next_attempt_at
            limit :max
            for update skip locked
            """, nativeQuery = true)
    List<Payout> claimDue(@Param("now") Instant now, @Param("max") int max);

    List<Payout> findByWalletId(String walletId);
}
