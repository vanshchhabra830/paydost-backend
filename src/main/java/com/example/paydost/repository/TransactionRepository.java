package com.example.paydost.repository;

import com.example.paydost.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Fetch all transactions where the user is either the sender or receiver,
     * ordered by timestamp descending (newest first). Paginated.
     */
    Page<Transaction> findBySenderIdOrReceiverIdOrderByTimestampDesc(
            Long senderId, Long receiverId, Pageable pageable);

    /**
     * Idempotency lookup: find a transaction by its client-generated referenceId.
     */
    Optional<Transaction> findByReferenceId(String referenceId);
}
