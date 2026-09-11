package com.wallet.admin;

import com.wallet.common.PagedResponse;
import com.wallet.exception.InvalidRequestException;
import com.wallet.exception.ResourceNotFoundException;
import com.wallet.transaction.TransactionFilter;
import com.wallet.transaction.TransactionPageResponse;
import com.wallet.user.Role;
import com.wallet.user.User;
import com.wallet.user.UserRepository;
import com.wallet.wallet.Wallet;
import com.wallet.wallet.WalletDtos;
import com.wallet.wallet.WalletRepository;
import com.wallet.wallet.WalletService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

import static com.wallet.admin.AdminDtos.UserAdminDto;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;

    public AdminService(UserRepository userRepository,
                        WalletRepository walletRepository,
                        WalletService walletService) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.walletService = walletService;
    }

    /** GET /api/v1/admin/users -> paginated list of all users. */
    @Transactional(readOnly = true)
    public PagedResponse<UserAdminDto> listUsers(int page, int size) {
        PageRequest pageRequest = PageRequest.of(clampPage(page), clampSize(size), Sort.by("createdAt").descending());
        Page<User> users = userRepository.findAll(pageRequest);
        return PagedResponse.from(users, AdminDtos::toUserAdminDto);
    }

    /** GET /api/v1/admin/users/{id} -> single user. */
    @Transactional(readOnly = true)
    public UserAdminDto getUser(Long id) {
        return AdminDtos.toUserAdminDto(requireUser(id));
    }

    /** GET /api/v1/admin/users/{id}/wallets -> all wallets of a user. */
    @Transactional(readOnly = true)
    public List<WalletDtos.WalletResponse> getUserWallets(Long userId) {
        requireUser(userId);
        return walletRepository.findAllByUserId(userId).stream()
                .map(AdminService::toWalletResponse)
                .toList();
    }

    /** GET /api/v1/admin/wallets -> paginated list of all wallets. */
    @Transactional(readOnly = true)
    public PagedResponse<WalletDtos.WalletResponse> listWallets(int page, int size) {
        PageRequest pageRequest = PageRequest.of(clampPage(page), clampSize(size), Sort.by("createdAt").descending());
        Page<Wallet> wallets = walletRepository.findAll(pageRequest);
        return PagedResponse.from(wallets, AdminService::toWalletResponse);
    }

    /** GET /api/v1/admin/wallets/{id}/transactions -> any wallet's history. */
    @Transactional(readOnly = true)
    public TransactionPageResponse getWalletTransactions(Long walletId, TransactionFilter filter, User actor) {
        return walletService.getTransactions(walletId, filter, actor);
    }

    /** PATCH /api/v1/admin/users/{id}/role -> change a user's role (USER/ADMIN). */
    @Transactional
    public UserAdminDto changeRole(Long id, String roleValue) {
        User user = requireUser(id);

        Role newRole;
        try {
            newRole = Role.valueOf(roleValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Role must be USER or ADMIN");
        }

        if (user.getRole() == newRole) {
            throw new InvalidRequestException("User already has role " + newRole);
        }

        user.setRole(newRole);
        userRepository.save(user);
        return AdminDtos.toUserAdminDto(user);
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private static WalletDtos.WalletResponse toWalletResponse(Wallet wallet) {
        return WalletDtos.WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUser().getId())
                .balance(wallet.getBalance())
                .createdAt(wallet.getCreatedAt())
                .build();
    }

    private static int clampPage(int page) {
        return Math.max(page, 0);
    }

    private static int clampSize(int size) {
        return size > 0 ? Math.min(size, 100) : 20;
    }
}