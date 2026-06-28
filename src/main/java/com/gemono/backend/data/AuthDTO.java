package com.gemono.backend.data;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public class AuthDTO {

    @Data
    public static class MagicLinkRequest {
        @Email(message = "Must be a valid email address")
        @NotBlank(message = "Email is required")
        private String email;
    }

    @Data
    public static class VerifyTokenRequest {
        @NotBlank(message = "Token is required")
        private String token;
    }

    // request body for PUT /api/auth/profile
    @Data
    public static class UpdateProfileRequest {
        @Size(max = 100, message = "Full name must be at most 100 characters")
        private String fullName;

        @Size(max = 100, message = "Display name must be at most 100 characters")
        private String displayName;

        @Size(max = 10, message = "Language code must be at most 10 characters")
        private String language;
    }

    @Data
    public static class AuthResponse {
        private String id;
        private String token;
        private String email;
        private String fullName;
        private String displayName;
        private String avatarUrl;
        private String role;
        private String language;

        public AuthResponse(String id, String token, String email, String fullName, String displayName,
                            String avatarUrl, String role, String language) {
            this.id          = id;
            this.token       = token;
            this.email       = email;
            this.fullName    = fullName;
            this.displayName = displayName;
            this.avatarUrl   = avatarUrl;
            this.role        = role;
            this.language    = language;
        }
    }
}