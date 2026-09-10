package com.wallet.wallet;

import com.wallet.common.IdempotencyService;
import com.wallet.exception.DuplicateResourceException;
import com.wallet.exception.InsufficientBalanceException;
import com.wallet.exception.InvalidRequestException;
import com.wallet.exception.ResourceNotFoundException;
import com.wallet.transaction.Transaction;
import com.wallet.transaction.TransactionFilter;
import com.wallet.transaction.TransactionPageResponse;
import com.wallet.transaction.TransactionRepository;
import com.wallet.transaction.TransactionResponse;
import com.wallet.transaction.TransactionStatus;
import com.wallet.transaction.TransactionType;
import com.wallet.user.User;
import com.wallet.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyService idempotencyService;

    public WalletService(WalletRepository walletRepository,
                         UserRepository userRepository,
                         TransactionRepository transactionRepository,
                         IdempotencyService idempotencyService) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Creates a wallet for a user (one wallet per user).
     */
    @Transactional
    public WalletDtos.WalletResponse createWallet(Long userId, WalletDtos.CreateWalletRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (walletRepository.existsByUserId(userId)) {
            throw new DuplicateResourceException("User already has a wallet");
        }

        BigDecimal initial = request == null || request.getInitialBalance() == null
                ? BigDecimal.ZERO
                : request.getInitialBalance();

        if (initial.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException("Initial balance cannot be negative");
        }

        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(initial)
                .build();
        walletRepository.save(wallet);

        return toResponse(wallet);
    }

    /**
     * DEPOSIT - add money to a wallet.
     * ACID: everything inside this @Transactional method either commits together
     * or rolls back together (balance update + transaction row).
     */
    @Transactional
    public TransactionResponse deposit(Long walletId, MoneyRequest request, String idempotencyKey, User actor) {
        // 1) Already processed? DB is the source of truth.
        Transaction existing = findTransaction(walletId, idempotencyKey);
        if (existing != null) {
            return TransactionResponse.from(existing);
        }

        // 2) Redis lock: fast duplicate guard for concurrent identical calls.
        boolean acquired = idempotencyService.tryAcquire(idempotencyKey);
        if (!acquired) {
            // Another request with the same key is running. By the time we get
            // here the other one may have committed -> re-check the DB.
            existing = findTransaction(walletId, idempotencyKey);
            if (existing != null) {
                return TransactionResponse.from(existing);
            }
            throw new InvalidRequestException("A request with idempotency key "
                    + idempotencyKey + " is already in progress");
        }

        try {
            // 3) OWNERSHIP check (plain read).
            Wallet wallet = walletRepository.findById(walletId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found with id: " + walletId));
            verifyOwner(wallet, actor);

            // 4) ATOMIC credit: single SQL statement, row-locked by PG itself.
            int rows = walletRepository.credit(walletId, request.getAmount());
            if (rows == 0) {
                throw new ResourceNotFoundException("Wallet not found with id: " + walletId);
            }

            // 5) Re-read the fresh balance for the audit row.
            BigDecimal newBalance = walletRepository.findById(walletId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found with id: " + walletId))
                    .getBalance();

            Transaction transaction = Transaction.builder()
                    .wallet(wallet)
                    .type(TransactionType.DEPOSIT)
                    .status(TransactionStatus.SUCCESS)
                    .amount(request.getAmount())
                    .balanceAfter(newBalance)
                    .idempotencyKey(idempotencyKey)
                    .description(request.getDescription())
                    .build();
            transactionRepository.save(transaction);

            return TransactionResponse.from(transaction);
        } catch (DataIntegrityViolationException e) {
            // Unique constraint (wallet_id, idempotency_key) tripped -> a parallel
            // identical request inserted it. Return the winner's result.
            existing = findTransaction(walletId, idempotencyKey);
            if (existing != null) {
                return TransactionResponse.from(existing);
            }
            throw e;
        } finally {
            idempotencyService.release(idempotencyKey);
        }
    }

    /**
     * WITHDRAW - take money out of a wallet.
     * Balance check happens INSIDE the lock to stay race-free.
     */
    @Transactional
    public TransactionResponse withdraw(Long walletId, MoneyRequest request, String idempotencyKey, User actor) {
        Transaction existing = findTransaction(walletId, idempotencyKey);
        if (existing != null) {
            return TransactionResponse.from(existing);
        }

        boolean acquired = idempotencyService.tryAcquire(idempotencyKey);
        if (!acquired) {
            existing = findTransaction(walletId, idempotencyKey);
            if (existing != null) {
                return TransactionResponse.from(existing);
            }
            throw new InvalidRequestException("A request with idempotency key "
                    + idempotencyKey + " is already in progress");
        }

        try {
            // 3) OWNERSHIP check (plain read, no lock needed for the owner bit).
            Wallet wallet = walletRepository.findById(walletId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found with id: " + walletId));
            verifyOwner(wallet, actor);

            // 4) ATOMIC debit: the whole "check balance + subtract" happens in ONE
            //    SQL statement. PostgreSQL locks the row for the statement, so
            //    concurrent withdraws serialize at the DB and can never both
            //    pass the balance guard at once (no lost updates, no overdraw).
            int rows = walletRepository.debitIfSufficient(walletId, request.getAmount());
            if (rows == 0) {
                Wallet fresh = walletRepository.findById(walletId)
                        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found with id: " + walletId));
                if (fresh.getBalance().compareTo(request.getAmount()) < 0) {
                    throw new InsufficientBalanceException(
                            "Insufficient balance: have " + fresh.getBalance()
                                    + ", need " + request.getAmount());
                }
                throw new InvalidRequestException("Could not withdraw amount " + request.getAmount());
            }

            // 5) Re-read the fresh balance for the audit row.
            BigDecimal newBalance = walletRepository.findById(walletId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found with id: " + walletId))
                    .getBalance();

            Transaction transaction = Transaction.builder()
                    .wallet(wallet)
                    .type(TransactionType.WITHDRAW)
                    .status(TransactionStatus.SUCCESS)
                    .amount(request.getAmount())
                    .balanceAfter(newBalance)
                    .idempotencyKey(idempotencyKey)
                    .description(request.getDescription())
                    .build();
            transactionRepository.save(transaction);

            return TransactionResponse.from(transaction);
        } catch (DataIntegrityViolationException e) {
            existing = findTransaction(walletId, idempotencyKey);
            if (existing != null) {
                return TransactionResponse.from(existing);
            }
            throw e;
        } finally {
            idempotencyService.release(idempotencyKey);
        }
    }

    private Transaction findTransaction(Long walletId, String idempotencyKey) {
        return transactionRepository.findByWalletIdAndIdempotencyKey(walletId, idempotencyKey)
                .orElse(null);
    }

    /** Owner or ADMIN may operate on a wallet. */
    private void verifyOwner(Wallet wallet, User actor) {
        boolean isOwner = wallet.getUser().getId().equals(actor.getId());
        boolean isAdmin = actor.getRole().name().equals("ADMIN");
        if (!isOwner && !isAdmin) {
            throw new com.wallet.exception.AccessDeniedException("You are not allowed to access this wallet");
        }
    }

    @Transactional(readOnly = true)
    public List<WalletDtos.WalletResponse> getWalletsByUser(Long userId) {
        return walletRepository.findAllByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WalletDtos.WalletResponse getWallet(Long id) {
        Wallet wallet = walletRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found with id: " + id));
        return toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public TransactionPageResponse getTransactions(Long walletId, TransactionFilter filter, User actor) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found with id: " + walletId));
        verifyOwner(wallet, actor);

        String type = null;
        if (filter.getType() != null && !filter.getType().isBlank()) {
            type = filter.getType().toUpperCase();
        }

        String status = null;
        if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
            status = filter.getStatus().toUpperCase();
        }

        String dateFrom = null;
        if (filter.getDateFrom() != null && !filter.getDateFrom().isBlank()) {
            LocalDate.parse(filter.getDateFrom());
            dateFrom = filter.getDateFrom();
        }

        String dateTo = null;
        if (filter.getDateTo() != null && !filter.getDateTo().isBlank()) {
            LocalDate.parse(filter.getDateTo());
            dateTo = filter.getDateTo();
        }

        int page = filter.getPage() != null && filter.getPage() >= 0 ? filter.getPage() : 0;
        int size = filter.getSize() != null && filter.getSize() > 0 ? Math.min(filter.getSize(), 100) : 20;

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Transaction> result = transactionRepository.findByFilters(walletId, type, status, dateFrom, dateTo, pageRequest);

        List<TransactionResponse> content = result.getContent().stream()
                .map(TransactionResponse::from)
                .toList();

        return new TransactionPageResponse(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private WalletDtos.WalletResponse toResponse(Wallet wallet) {
        return WalletDtos.WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUser().getId())
                .balance(wallet.getBalance())
                .createdAt(wallet.getCreatedAt())
                .build();
    }
}