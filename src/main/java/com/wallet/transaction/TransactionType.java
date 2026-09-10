package com.wallet.transaction;

/**
 * Type of wallet transaction.
 * DEPOSIT  -> money in
 * WITHDRAW -> money out
 * TRANSFER_IN / TRANSFER_OUT -> added in Phase 3
 */
public enum TransactionType {
    DEPOSIT,
    WITHDRAW,
    TRANSFER_IN,
    TRANSFER_OUT
}