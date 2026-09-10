package com.wallet.transaction;

import com.wallet.wallet.Wallet;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Financial record for every money movement.
 * Everything that changes a balance creates one of these rows,
 * so we can always audit: who, what, when, how much.
 */
@Entity
@Table(name = "transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_wallet_idempotency",
                columnNames = {"wallet_id", "idempotency_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** balance of the wallet AFTER this transaction */
    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    /**
     * Client-supplied unique key that makes the operation idempotent.
     * The DB-level unique constraint is our final safety net -
     * even if Redis fails, the same key can never create two deposits.
     */
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    /** Optional human-readable note (e.g. "transfer to bob") */
    @Column(length = 255)
    private String description;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}