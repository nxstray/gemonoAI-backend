package com.gemono.backend.unit;

import com.gemono.backend.data.AuthDTO;
import com.gemono.backend.model.MagicLinkToken;
import com.gemono.backend.model.User;
import com.gemono.backend.repository.MagicLinkTokenRepository;
import com.gemono.backend.security.JwtUtil;
import com.gemono.backend.service.*;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Epic("Authentication")
@Feature("AuthService")
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test — AuthService")
class AuthServiceTest {

    @Mock private UserService userService;
    @Mock private JwtUtil jwtUtil;
    @Mock private GuestService guestService;
    @Mock private ResendEmailService resendEmailService;
    @Mock private MagicLinkTokenRepository magicLinkTokenRepository;
    @InjectMocks private AuthService authService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(authService, "expiryMinutes", 15);
        ReflectionTestUtils.setField(authService, "baseUrl", "http://localhost:5173");
    }

    private User buildUser() {
        return User.builder()
                .email("test@example.com")
                .fullName("Test User")
                .role(User.Role.USER)
                .build();
    }

    // sendMagicLink
    @Test
    @Story("Magic Link")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-UNIT-07 | sendMagicLink invalidates old tokens and sends email")
    void TC_UNIT_07_sendMagicLinkInvalidatesOldAndSendsEmail() {
        authService.sendMagicLink("test@example.com", null);

        verify(magicLinkTokenRepository).invalidateAllForEmail("test@example.com");
        verify(magicLinkTokenRepository).save(any(MagicLinkToken.class));
        verify(resendEmailService).sendMagicLink(
                eq("test@example.com"), contains("localhost:5173/auth/verify?token="), eq(15));
    }

    @Test
    @Story("Magic Link")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-UNIT-08 | sendMagicLink stores guestId in token for later merge")
    void TC_UNIT_08_sendMagicLinkStoresGuestId() {
        authService.sendMagicLink("test@example.com", "guest-abc");

        ArgumentCaptor<MagicLinkToken> captor = ArgumentCaptor.forClass(MagicLinkToken.class);
        verify(magicLinkTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getGuestId()).isEqualTo("guest-abc");
    }

    // verifyMagicLink
    @Test
    @Story("Magic Link")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-UNIT-09 | verifyMagicLink returns JWT for valid token")
    void TC_UNIT_09_verifyMagicLinkReturnsJwt() {
        MagicLinkToken token = MagicLinkToken.builder()
                .email("test@example.com")
                .token("valid-token")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .consumed(false)
                .build();

        when(magicLinkTokenRepository.findByTokenAndConsumedFalse("valid-token"))
                .thenReturn(Optional.of(token));
        when(userService.findOrCreateEmailUser("test@example.com")).thenReturn(buildUser());
        when(jwtUtil.generateToken("test@example.com", "USER")).thenReturn("jwt-abc");

        AuthDTO.AuthResponse res = authService.verifyMagicLink("valid-token");

        assertThat(res.getToken()).isEqualTo("jwt-abc");
        assertThat(res.getEmail()).isEqualTo("test@example.com");
        // Token should be marked consumed
        assertThat(token.isConsumed()).isTrue();
    }

    @Test
    @Story("Magic Link")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-UNIT-10 | verifyMagicLink throws when token not found")
    void TC_UNIT_10_verifyMagicLinkThrowsWhenTokenNotFound() {
        when(magicLinkTokenRepository.findByTokenAndConsumedFalse("bad-token"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyMagicLink("bad-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    @Story("Magic Link")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-UNIT-11 | verifyMagicLink throws when token is expired")
    void TC_UNIT_11_verifyMagicLinkThrowsWhenExpired() {
        MagicLinkToken expired = MagicLinkToken.builder()
                .email("test@example.com")
                .token("expired-token")
                .expiresAt(LocalDateTime.now().minusMinutes(5)) // already expired
                .consumed(false)
                .build();

        when(magicLinkTokenRepository.findByTokenAndConsumedFalse("expired-token"))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.verifyMagicLink("expired-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @Story("Guest Merge")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-UNIT-12 | verifyMagicLink merges guest history when guestId present in token")
    void TC_UNIT_12_verifyMagicLinkMergesGuestHistory() {
        MagicLinkToken token = MagicLinkToken.builder()
                .email("test@example.com")
                .token("valid-token")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .guestId("guest-uuid-xyz")
                .consumed(false)
                .build();

        when(magicLinkTokenRepository.findByTokenAndConsumedFalse("valid-token"))
                .thenReturn(Optional.of(token));
        when(userService.findOrCreateEmailUser("test@example.com")).thenReturn(buildUser());
        when(jwtUtil.generateToken(any(), any())).thenReturn("jwt");

        authService.verifyMagicLink("valid-token");

        verify(guestService).mergeGuestHistory("guest-uuid-xyz", "test@example.com");
    }

    @Test
    @Story("Profile")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-UNIT-13 | getProfile returns user data without token")
    void TC_UNIT_13_getProfileReturnsUserData() {
        when(userService.findByEmail("test@example.com")).thenReturn(buildUser());

        AuthDTO.AuthResponse res = authService.getProfile("test@example.com");

        assertThat(res.getToken()).isNull();
        assertThat(res.getEmail()).isEqualTo("test@example.com");
        assertThat(res.getFullName()).isEqualTo("Test User");
    }
}