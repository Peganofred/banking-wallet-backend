package com.wallet.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
    List<Wallet> findAllByUserId(Long userId);

    /**
     * ATOMIC debit with a balance guard.
     * SQL: UPDATE wallets SET balance = balance - :amount
     *      WHERE id = :id AND balance >= :amount
     *
     * PostgreSQL turns this into a single atomic row-locking statement:
     * the row is locked as part of the UPDATE itself, so two concurrent
     * debits SERIALIZE at the database. The balance guard prevents
     * overdraw even under perfect concurrency.
     *
     * Returns the number of rows updated:
     *   1  -> money debited
     *   0  -> wallet does not exist OR insufficient balance
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update wallets set balance = balance - :amount " +
            "where id = :id and balance >= :amount", nativeQuery = true)
    int debitIfSufficient(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * ATOMIC credit: balance = balance + :amount.
     * Returns number of rows updated (1 = success, 0 = wallet missing).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update wallets set balance = balance + :amount where id = :id", nativeQuery = true)
    int credit(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
