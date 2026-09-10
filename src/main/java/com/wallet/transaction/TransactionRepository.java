package com.wallet.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByWalletIdAndIdempotencyKey(Long walletId, String idempotencyKey);

    boolean existsByWalletIdAndIdempotencyKey(Long walletId, String idempotencyKey);

    @Query(value = "SELECT * FROM transactions t " +
            "WHERE t.wallet_id = :walletId " +
            "AND (:type IS NULL OR t.type = :type) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:dateFrom IS NULL OR t.created_at >= CAST(:dateFrom AS timestamp)) " +
            "AND (:dateTo IS NULL OR t.created_at <= CAST(:dateTo AS timestamp)) " +
            "ORDER BY t.created_at DESC",
            countQuery = "SELECT count(*) FROM transactions t " +
            "WHERE t.wallet_id = :walletId " +
            "AND (:type IS NULL OR t.type = :type) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:dateFrom IS NULL OR t.created_at >= CAST(:dateFrom AS timestamp)) " +
            "AND (:dateTo IS NULL OR t.created_at <= CAST(:dateTo AS timestamp))",
            nativeQuery = true)
    Page<Transaction> findByFilters(@Param("walletId") Long walletId,
                                    @Param("type") String type,
                                    @Param("status") String status,
                                    @Param("dateFrom") String dateFrom,
                                    @Param("dateTo") String dateTo,
                                    Pageable pageable);
}