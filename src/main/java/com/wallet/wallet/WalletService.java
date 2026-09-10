package com.wallet.wallet;

import com.wallet.exception.DuplicateResourceException;
import com.wallet.exception.InvalidRequestException;
import com.wallet.exception.ResourceNotFoundException;
import com.wallet.user.User;
import com.wallet.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    public WalletService(WalletRepository walletRepository, UserRepository userRepository) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
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

    private WalletDtos.WalletResponse toResponse(Wallet wallet) {
        return WalletDtos.WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUser().getId())
                .balance(wallet.getBalance())
                .createdAt(wallet.getCreatedAt())
                .build();
    }
}