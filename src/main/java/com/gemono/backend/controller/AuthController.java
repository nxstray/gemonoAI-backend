package com.gemono.backend.controller;

import com.gemono.backend.data.AuthDTO;
import com.gemono.backend.service.AuthService;
import com.gemono.backend.data.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Passwordless authentication via magic link + Google OAuth")
public class AuthController {

    private final AuthService authService;

    // Step 1: User submits email → receives magic link in inbox
    @PostMapping("/magic-link")
    @Operation(summary = "Send magic link to email — no password needed")
    public ResponseEntity<ApiResponse<Void>> sendMagicLink(
            @Valid @RequestBody AuthDTO.MagicLinkRequest request,
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId) {
        authService.sendMagicLink(request.getEmail(), guestId);
        return ResponseEntity.ok(ApiResponse.ok("Login link sent. Check your inbox."));
    }

    // Step 2: User clicks link → frontend calls this with token → receives JWT
    @PostMapping("/verify")
    @Operation(summary = "Verify magic link token and receive JWT")
    public ResponseEntity<ApiResponse<AuthDTO.AuthResponse>> verifyMagicLink(
            @Valid @RequestBody AuthDTO.VerifyTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Login successful",
                authService.verifyMagicLink(request.getToken())));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<ApiResponse<AuthDTO.AuthResponse>> me(
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(ApiResponse.ok(authService.getProfile(email)));
    }
}