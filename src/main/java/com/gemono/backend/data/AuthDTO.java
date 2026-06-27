package com.gemono.backend.data;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDTO {

    // Request to send a magic link — only email needed, no password
    @Data
    public static class MagicLinkRequest {
        @Email(message = "Must be a valid email address")
        @NotBlank(message = "Email is required")
        private String email;
    }

    // Request to verify the magic link token from the email URL
    @Data
    public static class VerifyTokenRequest {
        @NotBlank(message = "Token is required")
        private String token;
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