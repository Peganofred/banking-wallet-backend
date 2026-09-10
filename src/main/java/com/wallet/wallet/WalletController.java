package com.wallet.wallet;

import com.wallet.exception.AccessDeniedException;
import com.wallet.exception.InvalidRequestException;
import com.wallet.transaction.TransactionFilter;
import com.wallet.transaction.TransactionPageResponse;
import com.wallet.transaction.TransactionResponse;
import com.wallet.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${app.api.prefix}/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    /**
     * Create a wallet for the CURRENT authenticated user.
     * -> POST /api/v1/wallets
     */
    @PostMapping
    public ResponseEntity<WalletDtos.WalletResponse> createWallet(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) @Valid WalletDtos.CreateWalletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(walletService.createWallet(user.getId(), request));
    }

    /**
     * Get all wallets of the current user.
     * -> GET /api/v1/wallets/my
     */
    @GetMapping("/my")
    public ResponseEntity<List<WalletDtos.WalletResponse>> myWallets(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(walletService.getWalletsByUser(user.getId()));
    }

    /**
     * Get one wallet by id.
     * Ownership check: user can only see their own wallet (or any wallet if ADMIN).
     * -> GET /api/v1/wallets/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<WalletDtos.WalletResponse> getWallet(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        WalletDtos.WalletResponse wallet = walletService.getWallet(id);
        verifyAccess(wallet.getUserId(), user);
        return ResponseEntity.ok(wallet);
    }

    /**
     * Get transaction history for a wallet (paginated, filterable).
     * -> GET /api/v1/wallets/{id}/transactions?page=0&size=20&type=DEPOSIT&dateFrom=2026-09-01
     */
    @GetMapping("/{id}/transactions")
    public ResponseEntity<TransactionPageResponse> getTransactions(
            @PathVariable Long id,
            @ModelAttribute TransactionFilter filter,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(walletService.getTransactions(id, filter, user));
    }

    /**
     * DEPOSIT money into a wallet.
     * -> POST /api/v1/wallets/{id}/deposit
     * Header: Idempotency-Key: <any-unique-string>
     */
    @PostMapping("/{id}/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MoneyRequest request) {
        requireIdempotencyKey(idempotencyKey);
        return ResponseEntity.ok(walletService.deposit(id, request, idempotencyKey, user));
    }

    /**
     * WITHDRAW money from a wallet.
     * -> POST /api/v1/wallets/{id}/withdraw
     * Header: Idempotency-Key: <any-unique-string>
     */
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable Long id,
            @AuthenticationPrincipal User user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MoneyRequest request) {
        requireIdempotencyKey(idempotencyKey);
        return ResponseEntity.ok(walletService.withdraw(id, request, idempotencyKey, user));
    }

    private void verifyAccess(Long ownerUserId, User user) {
        boolean isOwner = ownerUserId.equals(user.getId());
        boolean isAdmin = user.getRole().name().equals("ADMIN");
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You are not allowed to access this wallet");
        }
    }

    private void requireIdempotencyKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new InvalidRequestException("Idempotency-Key header is required for money operations");
        }
    }
}