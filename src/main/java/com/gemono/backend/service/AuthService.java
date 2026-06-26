package com.gemono.backend.service;

import com.gemono.backend.data.AuthDTO;
import com.gemono.backend.model.User;
import com.gemono.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final GuestService guestService;

    // Register new user, optionally merge guest history
    public AuthDTO.AuthResponse register(AuthDTO.RegisterRequest request, String guestId) {
        User user = userService.registerUser(
                request.getEmail(),
                request.getFullName(),
                request.getPassword()
        );

        if (guestId != null && !guestId.isBlank()) {
            guestService.mergeGuestHistory(guestId, user.getEmail());
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return new AuthDTO.AuthResponse(token, user.getEmail(), user.getFullName(),
                user.getAvatarUrl(), user.getRole().name());
    }

    // Login, optionally merge guest history
    public AuthDTO.AuthResponse login(AuthDTO.LoginRequest request, String guestId) {
        User user = userService.findByEmail(request.getEmail());

        if (user.getPassword() == null) {
            throw new IllegalArgumentException("Please sign in with Google");
        }

        if (!userService.checkPassword(user, request.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (guestId != null && !guestId.isBlank()) {
            guestService.mergeGuestHistory(guestId, user.getEmail());
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return new AuthDTO.AuthResponse(token, user.getEmail(), user.getFullName(),
                user.getAvatarUrl(), user.getRole().name());
    }

    public AuthDTO.AuthResponse getProfile(String email) {
        User user = userService.findByEmail(email);
        return new AuthDTO.AuthResponse(null, user.getEmail(), user.getFullName(),
                user.getAvatarUrl(), user.getRole().name());
    }
}