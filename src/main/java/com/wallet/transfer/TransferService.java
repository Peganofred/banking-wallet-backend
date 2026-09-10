package com.wallet.transfer;

import com.wallet.common.IdempotencyService;
import com.wallet.exception.AccessDeniedException;
import com.wallet.exception.InsufficientBalanceException;
import com.wallet.exception.InvalidRequestException;
import com.wallet.exception.ResourceNotFoundException;
import com.wallet.transaction.Transaction;
import com.wallet.transaction.TransactionRepository;
import com.wallet.transaction.TransactionResponse;
import com.wallet.transaction.TransactionStatus;
import com.wallet.transaction.TransactionType;
import com.wallet.user.User;
import com.wallet.wallet.Wallet;
import com.wallet.wallet.WalletRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransferService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyService idempotencyService;

    public TransferService(WalletRepository walletRepository,
                           TransactionRepository transactionRepository,
                           IdempotencyService idempotencyService) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyService = idempotencyService;
    }

    /**
     * TRANSFER money from the actor's wallet to a recipient wallet.
     *
     * Correctness strategy (this is what makes it race-free):
     * - Both legs are ATOMIC single-statement SQL updates:
     *     sender:   UPDATE wallets SET balance = balance - :amt WHERE id=:id AND balance >= :amt
     *     receiver: UPDATE wallets SET balance = balance + :amt WHERE id=:id
     *   PostgreSQL locks the row inside the UPDATE itself, so concurrent
     *   transfers on the same wallet serialize AT THE DATABASE. There is no
     *   read-then-write window, therefore no lost updates and no way for two
     *   debits to both see the same balance.
     * - Atomicity/durability: the whole method runs in ONE @Transactional -
     *   debit + credit + 2 audit rows EITHER all commit or all roll back.
     * - Deadlock prevention: when a single transaction touches two wallets it
     *   does the LOWER id first, so concurrent transfers always lock rows in
     *   the same global order (never A->B while another does B->A).
     */
    @Transactional
    public TransactionResponse transfer(Long senderWalletId, Long recipientWalletId,
                                        TransferRequest request, String idempotencyKey, User actor) {

        if (senderWalletId.equals(recipientWalletId)) {
            throw new InvalidRequestException("Cannot transfer to the same wallet");
        }

        // 1) Already done? DB is the source of truth (sender row is the anchor).
        Transaction existing = transactionRepository
                .findByWalletIdAndIdempotencyKey(senderWalletId, idempotencyKey)
                .orElse(null);
        if (existing != null) {
            return TransactionResponse.from(existing);
        }

        // 2) Redis lock: fast duplicate guard for concurrent identical calls.
        boolean acquired = idempotencyService.tryAcquire(idempotencyKey);
        if (!acquired) {
            existing = transactionRepository
                    .findByWalletIdAndIdempotencyKey(senderWalletId, idempotencyKey)
                    .orElse(null);
            if (existing != null) {
                return TransactionResponse.from(existing);
            }
            throw new InvalidRequestException("A request with idempotency key "
                    + idempotencyKey + " is already in progress");
        }

        try {
            // 3) Validate both wallets exist and the actor owns the sender.
            Wallet sender = walletRepository.findById(senderWalletId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found with id: " + senderWalletId));
            walletRepository.findById(recipientWalletId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found with id: " + recipientWalletId));
            verifyOwner(sender, actor);

            // 4) ATOMIC debit - process the LOWER wallet id first to avoid deadlocks.
            Wallet lower = walletRepository.findById(Math.min(senderWalletId, recipientWalletId)).orElseThrow();
            Wallet upper = walletRepository.findById(Math.max(senderWalletId, recipientWalletId)).orElseThrow();
            boolean lowerIsSender = lower.getId().equals(senderWalletId);

            int debitRows;
            int creditRows;
            if (lowerIsSender) {
                debitRows = walletRepository.debitIfSufficient(lower.getId(), request.getAmount());
                creditRows = walletRepository.credit(upper.getId(), request.getAmount());
            } else {
                creditRows = walletRepository.credit(lower.getId(), request.getAmount());
                debitRows = walletRepository.debitIfSufficient(upper.getId(), request.getAmount());
            }

            if (debitRows == 0) {
                // Distinguish: missing wallet vs insufficient funds.
                Wallet freshSender = walletRepository.findById(senderWalletId).orElseThrow(
                        () -> new ResourceNotFoundException("Wallet not found with id: " + senderWalletId));
                if (freshSender.getBalance().compareTo(request.getAmount()) < 0) {
                    throw new InsufficientBalanceException(
                            "Insufficient balance: have " + freshSender.getBalance()
                                    + ", need " + request.getAmount());
                }
                throw new InvalidRequestException("Could not debit amount " + request.getAmount());
            }
            if (creditRows == 0) {
                throw new ResourceNotFoundException("Wallet not found with id: " + recipientWalletId);
            }

            // 5) Re-read fresh balances for the audit rows.
            BigDecimal senderAfter = walletRepository.findById(senderWalletId).orElseThrow().getBalance();
            BigDecimal recipientAfter = walletRepository.findById(recipientWalletId).orElseThrow().getBalance();

            Wallet recipient = walletRepository.findById(recipientWalletId).orElseThrow();

            Transaction outTx = Transaction.builder()
                    .wallet(sender)
                    .type(TransactionType.TRANSFER_OUT)
                    .status(TransactionStatus.SUCCESS)
                    .amount(request.getAmount())
                    .balanceAfter(senderAfter)
                    .idempotencyKey(idempotencyKey)
                    .description("transfer to wallet " + recipientWalletId
                            + (request.getDescription() == null ? "" : " - " + request.getDescription()))
                    .build();

            Transaction inTx = Transaction.builder()
                    .wallet(recipient)
                    .type(TransactionType.TRANSFER_IN)
                    .status(TransactionStatus.SUCCESS)
                    .amount(request.getAmount())
                    .balanceAfter(recipientAfter)
                    .idempotencyKey(idempotencyKey)
                    .description("transfer from wallet " + senderWalletId
                            + (request.getDescription() == null ? "" : " - " + request.getDescription()))
                    .build();

            transactionRepository.save(outTx);
            transactionRepository.save(inTx);

            return TransactionResponse.from(outTx);
        } catch (DataIntegrityViolationException e) {
            existing = transactionRepository
                    .findByWalletIdAndIdempotencyKey(senderWalletId, idempotencyKey)
                    .orElse(null);
            if (existing != null) {
                return TransactionResponse.from(existing);
            }
            throw e;
        } finally {
            idempotencyService.release(idempotencyKey);
        }
    }

    /** Only the wallet owner (or ADMIN) may send money out of a wallet. */
    private void verifyOwner(Wallet wallet, User actor) {
        boolean isOwner = wallet.getUser().getId().equals(actor.getId());
        boolean isAdmin = actor.getRole().name().equals("ADMIN");
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You are not allowed to access this wallet");
        }
    }
}