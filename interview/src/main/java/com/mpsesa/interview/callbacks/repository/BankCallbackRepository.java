package com.mpsesa.interview.callbacks.repository;

import com.mpsesa.interview.callbacks.domain.BankCallback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankCallbackRepository extends JpaRepository<BankCallback, String> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into bank_callbacks (event_id, payout_id, status, received_at)
            values (:eventId, :payoutId, :status, now())
            on conflict (event_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("payoutId") String payoutId,
                       @Param("status") String status);
}
