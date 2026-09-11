package com.wallet.admin;

import com.wallet.common.PagedResponse;
import com.wallet.transaction.TransactionFilter;
import com.wallet.transaction.TransactionPageResponse;
import com.wallet.user.User;
import com.wallet.wallet.WalletDtos;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.wallet.admin.AdminDtos.UserAdminDto;

/**
 * Admin console. Every endpoint here is ADMIN-only (see SecurityConfig).
 * Base path: /api/v1/admin
 */
@RestController
@RequestMapping("${app.api.prefix}/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** GET /api/v1/admin/users?page=0&size=20 */
    @GetMapping("/users")
    public ResponseEntity<PagedResponse<UserAdminDto>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.listUsers(page, size));
    }

    /** GET /api/v1/admin/users/{id} */
    @GetMapping("/users/{id}")
    public ResponseEntity<UserAdminDto> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUser(id));
    }

    /** GET /api/v1/admin/users/{id}/wallets */
    @GetMapping("/users/{id}/wallets")
    public ResponseEntity<List<WalletDtos.WalletResponse>> getUserWallets(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUserWallets(id));
    }

    /** GET /api/v1/admin/wallets?page=0&size=20 */
    @GetMapping("/wallets")
    public ResponseEntity<PagedResponse<WalletDtos.WalletResponse>> listWallets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.listWallets(page, size));
    }

    /** GET /api/v1/admin/wallets/{id}/transactions */
    @GetMapping("/wallets/{id}/transactions")
    public ResponseEntity<TransactionPageResponse> getWalletTransactions(
            @PathVariable Long id,
            @ModelAttribute TransactionFilter filter,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(adminService.getWalletTransactions(id, filter, actor));
    }

    /** PATCH /api/v1/admin/users/{id}/role  body: {"role": "ADMIN"} */
    @PatchMapping("/users/{id}/role")
    public ResponseEntity<UserAdminDto> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody AdminDtos.RoleChangeRequest request) {
        return ResponseEntity.ok(adminService.changeRole(id, request.getRole()));
    }
}