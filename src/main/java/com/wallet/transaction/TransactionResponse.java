package com.wallet.transaction;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record TransactionResponse(
        Long id,
        Long walletId,
        String type,
        String status,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String idempotencyKey,
        String description,
        LocalDateTime createdAt
) {
    public static TransactionResponse from(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .walletId(t.getWallet().getId())
                .type(t.getType().name())
                .status(t.getStatus().name())
                .amount(t.getAmount())
                .balanceAfter(t.getBalanceAfter())
                .idempotencyKey(t.getIdempotencyKey())
                .description(t.getDescription())
                .createdAt(t.getCreatedAt())
                .build();
    }
}