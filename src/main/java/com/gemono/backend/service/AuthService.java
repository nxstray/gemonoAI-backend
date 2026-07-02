package com.gemono.backend.service;

import com.gemono.backend.data.AuthDTO;
import com.gemono.backend.model.MagicLinkToken;
import com.gemono.backend.model.User;
import com.gemono.backend.repository.MagicLinkTokenRepository;
import com.gemono.backend.repository.UserRepository;
import com.gemono.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final GuestService guestService;
    private final EmailService resendEmailService;
    private final MagicLinkTokenRepository magicLinkTokenRepository;
    private final UserRepository userRepository;

    @Value("${magic.link.expiry.minutes:15}")
    private int expiryMinutes;

    @Value("${magic.link.base-url:http://localhost:5173}")
    private String baseUrl;

    public void sendMagicLink(String email, String guestId) {
        magicLinkTokenRepository.invalidateAllForEmail(email);

        String rawToken = UUID.randomUUID().toString();

        MagicLinkToken linkToken = MagicLinkToken.builder()
                .email(email)
                .token(rawToken)
                .expiresAt(LocalDateTime.now().plusMinutes(expiryMinutes))
                .guestId(guestId)
                .build();

        magicLinkTokenRepository.save(linkToken);

        String magicUrl = baseUrl + "/auth/verify?token=" + rawToken;
        resendEmailService.sendMagicLink(email, magicUrl, expiryMinutes);
    }

    public AuthDTO.AuthResponse verifyMagicLink(String rawToken) {
        MagicLinkToken linkToken = magicLinkTokenRepository
                .findByTokenAndConsumedFalse(rawToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired login link."));

        if (linkToken.isExpired()) {
            throw new IllegalArgumentException("This login link has expired. Please request a new one.");
        }

        linkToken.setConsumed(true);
        magicLinkTokenRepository.save(linkToken);

        User user = userService.findOrCreateEmailUser(linkToken.getEmail());

        if (linkToken.getGuestId() != null && !linkToken.getGuestId().isBlank()) {
            guestService.mergeGuestHistory(linkToken.getGuestId(), user.getEmail());
        }

        String jwt = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return toAuthResponse(jwt, user);
    }

    public AuthDTO.AuthResponse getProfile(String email) {
        User user = userService.findByEmail(email);
        return toAuthResponse(null, user);
    }

    @Transactional
    public AuthDTO.AuthResponse updateProfile(String email, AuthDTO.UpdateProfileRequest req) {
        User user = userService.findByEmail(email);

        if (req.getFullName() != null && !req.getFullName().isBlank()) {
            user.setFullName(req.getFullName().trim());
        }
        if (req.getDisplayName() != null) {
            user.setDisplayName(req.getDisplayName().trim());
        }
        if (req.getLanguage() != null && !req.getLanguage().isBlank()) {
            user.setLanguage(req.getLanguage().trim());
        }

        userRepository.save(user);
        return toAuthResponse(null, user);
    }

    // cleanup expired tokens every hour
    @Scheduled(fixedRate = 3_600_000)
    public void cleanupExpiredTokens() {
        magicLinkTokenRepository.deleteExpiredAndConsumed(LocalDateTime.now());
    }

    // helper — build AuthResponse from User
    private AuthDTO.AuthResponse toAuthResponse(String jwt, User user) {
        return new AuthDTO.AuthResponse(
                user.getId() != null ? user.getId().toString() : null,
                jwt,
                user.getEmail(),
                user.getFullName(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole().name(),
                user.getLanguage()
        );
    }
}