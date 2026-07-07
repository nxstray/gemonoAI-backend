package com.gemono.backend.unit;

import com.gemono.backend.security.JwtUtil;
import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@Epic("Security")
@Feature("JwtUtil")
@DisplayName("Unit Test — JwtUtil")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setup() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
            "gemono-super-secret-key-for-testing-min-256-bits-long-enough");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400000L);
    }

    @Test
    @Story("Token Generation")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-UNIT-13 | generateToken produces non-null, non-blank token")
    void TC_UNIT_13_generateTokenProducesValidString() {
        String token = jwtUtil.generateToken("user@example.com", "USER");

        assertThat(token).isNotNull().isNotBlank();
        // JWT has 3 parts separated by dots
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @Story("Token Parsing")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-UNIT-14 | extractEmail returns correct email from token")
    void TC_UNIT_14_extractEmailFromToken() {
        String token = jwtUtil.generateToken("user@example.com", "USER");
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("user@example.com");
    }

    @Test
    @Story("Token Parsing")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-UNIT-15 | extractRole returns correct role from token")
    void TC_UNIT_15_extractRoleFromToken() {
        String token = jwtUtil.generateToken("admin@example.com", "ADMIN");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    @Story("Token Validation")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-UNIT-16 | isTokenValid returns true for valid token")
    void TC_UNIT_16_validTokenReturnsTrue() {
        String token = jwtUtil.generateToken("user@example.com", "USER");
        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    @Story("Token Validation")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-UNIT-17 | isTokenValid returns false for tampered token")
    void TC_UNIT_17_tamperedTokenReturnsFalse() {
        String token = jwtUtil.generateToken("user@example.com", "USER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtUtil.isTokenValid(tampered)).isFalse();
    }

    @Test
    @Story("Token Validation")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-UNIT-18 | isTokenValid returns false for empty string")
    void TC_UNIT_18_emptyStringReturnsFalse() {
        assertThat(jwtUtil.isTokenValid("")).isFalse();
        assertThat(jwtUtil.isTokenValid("not.a.jwt")).isFalse();
    }
}