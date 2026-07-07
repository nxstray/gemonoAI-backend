package com.gemono.backend.functional;

import com.gemono.backend.model.MagicLinkToken;
import com.gemono.backend.repository.MagicLinkTokenRepository;
import com.gemono.backend.service.AuthService;
import com.gemono.backend.service.ResendEmailService;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Functional Tests — verify specific magic link behaviors in detail.
 * Tests the business rules, not just happy paths.
 *
 * Run: mvn test -Dgroups=functional
 */
@Epic("Functional")     
@Feature("Magic Link Token Business Rules")
@Tag("functional")
@ExtendWith(MockitoExtension.class)
@DisplayName("Functional Test — Magic Link Flow")
class MagicLinkFunctionalTest {

    @Mock private MagicLinkTokenRepository tokenRepo;
    @Mock private ResendEmailService resendEmailService;
    @Mock private com.gemono.backend.service.UserService userService;
    @Mock private com.gemono.backend.service.GuestService guestService;
    @Mock private com.gemono.backend.security.JwtUtil jwtUtil;
    @InjectMocks private AuthService authService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(authService, "expiryMinutes", 15);
        ReflectionTestUtils.setField(authService, "baseUrl", "http://localhost:5173");
    }

    // TC-FUNC-01

    @Test
    @Story("Token Lifecycle")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-FUNC-01 | Sending magic link invalidates all previous tokens for same email")
    void TC_FUNC_01_sendMagicLinkInvalidatesPreviousTokens() {
        authService.sendMagicLink("user@example.com", null);

        // Must call invalidate BEFORE saving new token
        InOrder order = inOrder(tokenRepo);
        order.verify(tokenRepo).invalidateAllForEmail("user@example.com");
        order.verify(tokenRepo).save(any(MagicLinkToken.class));
    }

    // TC-FUNC-02

    @Test
    @Story("Token Lifecycle")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-FUNC-02 | Magic link URL contains the token and correct base URL")
    void TC_FUNC_02_magicLinkUrlContainsTokenAndBaseUrl() {
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

        authService.sendMagicLink("user@example.com", null);

        verify(resendEmailService).sendMagicLink(
                eq("user@example.com"),
                urlCaptor.capture(),
                eq(15)
        );

        String sentUrl = urlCaptor.getValue();
        assertThat(sentUrl).startsWith("http://localhost:5173/auth/verify?token=");
        assertThat(sentUrl).contains("token=");
        // Token part should be UUID format (36 chars)
        String tokenPart = sentUrl.split("token=")[1];
        assertThat(tokenPart).hasSize(36);
        assertThat(tokenPart).matches("[0-9a-f-]{36}");
    }

    // TC-FUNC-03

    @Test
    @Story("Token Lifecycle")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-FUNC-03 | Token expiry is set to exactly 15 minutes from now")
    void TC_FUNC_03_tokenExpiryIsCorrect() {
        ArgumentCaptor<MagicLinkToken> captor = ArgumentCaptor.forClass(MagicLinkToken.class);

        authService.sendMagicLink("user@example.com", null);

        verify(tokenRepo).save(captor.capture());
        MagicLinkToken saved = captor.getValue();

        LocalDateTime now = LocalDateTime.now();
        // Allow 2 seconds tolerance for test execution time
        assertThat(saved.getExpiresAt()).isBetween(
                now.plusMinutes(14).plusSeconds(58),
                now.plusMinutes(15).plusSeconds(2)
        );
    }

    // ── TC-FUNC-04 ────────────────────────────────────────────────────

    @Test
    @Story("Token Verification")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-FUNC-04 | Verified token is marked consumed immediately")
    void TC_FUNC_04_verifiedTokenIsMarkedConsumed() {
        MagicLinkToken token = MagicLinkToken.builder()
                .email("user@example.com")
                .token("test-token")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .consumed(false)
                .build();

        when(tokenRepo.findByTokenAndConsumedFalse("test-token"))
                .thenReturn(Optional.of(token));
        when(userService.findOrCreateEmailUser("user@example.com"))
                .thenReturn(com.gemono.backend.model.User.builder()
                        .email("user@example.com")
                        .fullName("User")
                        .role(com.gemono.backend.model.User.Role.USER)
                        .build());
        when(jwtUtil.generateToken(any(), any())).thenReturn("jwt");

        authService.verifyMagicLink("test-token");

        assertThat(token.isConsumed()).isTrue();
        verify(tokenRepo).save(token);
    }

    // ── TC-FUNC-05 ────────────────────────────────────────────────────

    @Test
    @Story("Token Verification")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-FUNC-05 | Consumed token cannot be verified again (one-time use)")
    void TC_FUNC_05_consumedTokenCannotBeReused() {
        // findByTokenAndConsumedFalse returns empty because token.consumed = true
        when(tokenRepo.findByTokenAndConsumedFalse("already-used"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyMagicLink("already-used"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    // ── TC-FUNC-06 ────────────────────────────────────────────────────

    @Test
    @Story("User Auto-Creation")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-FUNC-06 | New user is auto-created on first magic link verification")
    void TC_FUNC_06_newUserAutoCreatedOnFirstVerify() {
        MagicLinkToken token = MagicLinkToken.builder()
                .email("newuser@example.com")
                .token("new-user-token")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .consumed(false)
                .build();

        when(tokenRepo.findByTokenAndConsumedFalse("new-user-token"))
                .thenReturn(Optional.of(token));

        com.gemono.backend.model.User newUser = com.gemono.backend.model.User.builder()
                .email("newuser@example.com")
                .fullName("Newuser")
                .role(com.gemono.backend.model.User.Role.USER)
                .build();

        when(userService.findOrCreateEmailUser("newuser@example.com")).thenReturn(newUser);
        when(jwtUtil.generateToken(any(), any())).thenReturn("jwt");

        authService.verifyMagicLink("new-user-token");

        // findOrCreate must be called — not just findByEmail
        verify(userService).findOrCreateEmailUser("newuser@example.com");
    }

    // TC-FUNC-07
    @Test
    @Story("Guest Merge")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-FUNC-07 | Guest history is NOT merged when token has no guestId")
    void TC_FUNC_07_guestHistoryNotMergedWhenNoGuestId() {
        MagicLinkToken token = MagicLinkToken.builder()
                .email("user@example.com")
                .token("no-guest-token")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .guestId(null) // no guest ID
                .consumed(false)
                .build();

        when(tokenRepo.findByTokenAndConsumedFalse("no-guest-token"))
                .thenReturn(Optional.of(token));
        when(userService.findOrCreateEmailUser(any()))
                .thenReturn(com.gemono.backend.model.User.builder()
                        .email("user@example.com")
                        .fullName("User")
                        .role(com.gemono.backend.model.User.Role.USER)
                        .build());
        when(jwtUtil.generateToken(any(), any())).thenReturn("jwt");

        authService.verifyMagicLink("no-guest-token");

        verify(guestService, never()).mergeGuestHistory(any(), any());
    }
}