package com.wallet.wallet;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Request body for deposit / withdraw / transfer amount operations.
 * Idempotency-Key is sent as HTTP HEADER (not body) - see controller.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MoneyRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    private String description;
}