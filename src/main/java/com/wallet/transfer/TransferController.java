package com.wallet.transfer;

import com.wallet.exception.InvalidRequestException;
import com.wallet.transaction.TransactionResponse;
import com.wallet.user.User;
import com.wallet.wallet.Wallet;
import com.wallet.wallet.WalletRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint: transfer money from the CURRENT user's wallet to another wallet.
 * -> POST /api/v1/transfers
 * Headers: Authorization: Bearer <jwt> ,  Idempotency-Key: <unique>
 */
@RestController
@RequestMapping("${app.api.prefix}/transfers")
public class TransferController {

    private final TransferService transferService;
    private final WalletRepository walletRepository;

    public TransferController(TransferService transferService, WalletRepository walletRepository) {
        this.transferService = transferService;
        this.walletRepository = walletRepository;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> transfer(
            @AuthenticationPrincipal User actor,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        checkKey(idempotencyKey);

        // Sender is always the CURRENT user's wallet.
        Wallet sender = walletRepository.findByUserId(actor.getId())
                .orElseThrow(() -> new com.wallet.exception.ResourceNotFoundException(
                        "You do not have a wallet yet - create one first"));

        TransactionResponse result = transferService.transfer(
                sender.getId(), request.getToWalletId(), request, idempotencyKey, actor);
        return ResponseEntity.ok(result);
    }

    private void checkKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new InvalidRequestException("Idempotency-Key header is required for money operations");
        }
    }
}