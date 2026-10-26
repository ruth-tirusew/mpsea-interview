package com.mpsesa.interview.transfers.repository;

import com.mpsesa.interview.transfers.domain.Reversal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReversalRepository extends JpaRepository<Reversal, Long> {

    Optional<Reversal> findByTransferId(Long transferId);

    boolean existsByTransferId(Long transferId);
}
