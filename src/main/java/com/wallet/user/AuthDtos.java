package com.wallet.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class AuthDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {
        @jakarta.validation.constraints.Email(message = "Invalid email format")
        @jakarta.validation.constraints.NotBlank(message = "Email is required")
        private String email;

        @jakarta.validation.constraints.NotBlank(message = "Password is required")
        @jakarta.validation.constraints.Size(min = 6, message = "Password must be at least 6 characters")
        private String password;

        @jakarta.validation.constraints.NotBlank(message = "Name is required")
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @jakarta.validation.constraints.Email(message = "Invalid email format")
        @jakarta.validation.constraints.NotBlank(message = "Email is required")
        private String email;

        @jakarta.validation.constraints.NotBlank(message = "Password is required")
        private String password;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private String token;
        private Long userId;
        private String email;
        private String name;
        private String role;
    }
}
