package com.gemono.backend.data;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDTO {

    @Data
    public static class RegisterRequest {
        @Email
        @NotBlank
        private String email;
        @NotBlank
        private String fullName;
        @NotBlank
        private String password;
    }

    @Data
    public static class LoginRequest {
        @Email
        @NotBlank
        private String email;
        @NotBlank
        private String password;
    }

    @Data
    public static class AuthResponse {
        private String token;
        private String email;
        private String fullName;
        private String avatarUrl;
        private String role;

        public AuthResponse(String token, String email, String fullName, String avatarUrl, String role) {
            this.token = token;
            this.email = email;
            this.fullName = fullName;
            this.avatarUrl = avatarUrl;
            this.role = role;
        }
    }
}