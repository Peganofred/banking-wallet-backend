package com.wallet.admin;

import com.wallet.user.User;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/** DTOs for the admin console. */
public class AdminDtos {

    /** A user row shown in the admin user list. */
    @Getter
    @Setter
    public static class UserAdminDto {
        private Long id;
        private String email;
        private String name;
        private String role;
        private LocalDateTime createdAt;
    }

    /** Request body to change a user's role -> PATCH /admin/users/{id}/role */
    @Getter
    @Setter
    public static class RoleChangeRequest {
        @NotBlank(message = "Role is required")
        private String role; // "ADMIN" or "USER"
    }

    public static UserAdminDto toUserAdminDto(User u) {
        UserAdminDto dto = new UserAdminDto();
        dto.setId(u.getId());
        dto.setEmail(u.getEmail());
        dto.setName(u.getName());
        dto.setRole(u.getRole().name());
        dto.setCreatedAt(u.getCreatedAt());
        return dto;
    }
}
