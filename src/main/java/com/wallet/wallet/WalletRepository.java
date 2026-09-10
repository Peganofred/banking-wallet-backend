package com.wallet.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
    List<Wallet> findAllByUserId(Long userId);

    /**
     * PESSIMISTIC (row) locking:
     * SELECT ... FOR UPDATE on the wallet row.
     *
     * While this transaction holds the lock:
     * - Another concurrent deposit/withdraw/transfer on the SAME wallet
     *   will BLOCK and wait here (no lost updates, no negative balances).
     *
     * This is the classic choice for financial writes where correctness
     * matters more than throughput.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") Long id);
}
