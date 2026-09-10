package com.wallet.wallet;

import com.wallet.exception.AccessDeniedException;
import com.wallet.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
        if (!wallet.getUserId().equals(user.getId()) && !isAdmin(user)) {
            throw new AccessDeniedException("You are not allowed to access this wallet");
        }
        return ResponseEntity.ok(wallet);
    }

    private boolean isAdmin(User user) {
        return user.getRole().name().equals("ADMIN");
    }
}