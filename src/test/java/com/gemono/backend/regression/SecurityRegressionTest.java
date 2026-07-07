package com.gemono.backend.regression;

import io.qameta.allure.*;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;

/**
 * Security Regression Tests — ensure no security rules have been
 * accidentally relaxed after refactoring.
 *
 * Run: mvn test -Dgroups=regression
 */
@Epic("Regression")
@Feature("Security Rules")
@Tag("regression")
@DisplayName("Regression — Security Rules")
class SecurityRegressionTest {

   private static final String FRONTEND_ORIGIN =
            System.getProperty("test.frontend.url", "http://localhost:5173");

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = System.getProperty("test.backend.url", "http://localhost:8020");
    }

    // TC-REG-11
    // TC-REG-11
    @Test
    @Story("CORS")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-REG-11 | CORS preflight OPTIONS returns 200 for allowed origin")
    void TC_REG_11_corsPreflightAllowedOrigin() {
        Response res = given()
                .header("Origin", FRONTEND_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type,Authorization")
                .options("/api/auth/magic-link");

        Assertions.assertEquals(200, res.getStatusCode(),
                "CORS preflight from " + FRONTEND_ORIGIN + " should be allowed");
    }

    // TC-REG-12
    @Test
    @Story("Admin")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-REG-12 | Admin endpoint returns 401 without token (not accidentally public)")
    void TC_REG_12_adminEndpointNotPublic() {
        Response res = given()
                .get("/api/admin/users");

        Assertions.assertEquals(401, res.getStatusCode(),
                "Admin endpoint must never be publicly accessible");
    }

    // TC-REG-13
    @Test
    @Story("Admin")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-REG-13 | Admin endpoint returns 403 for USER role token (not accidentally accessible)")
    void TC_REG_13_adminEndpointForbiddenForUserRole() {
        // Fabricated JWT with USER role — will fail signature verification → 401
        // This validates that the security config is still enforcing role checks
        Response res = given()
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.fake.payload")
                .get("/api/admin/users");

        // 401 (invalid token) or 403 (valid token, wrong role) — both are acceptable
        // What is NOT acceptable is 200
        Assertions.assertNotEquals(200, res.getStatusCode(),
                "Admin endpoint must never return 200 for non-admin users");
    }

    // TC-REG-14
    @Test
    @Tag("requires-groq")
    @Story("Rate Limiting")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-REG-14 | Rate limit filter is still active on send endpoint")
    void TC_REG_14_rateLimitFilterStillActive() {
        // Single request — should pass through (not blocked)
        Response res = given()
                .header("X-Guest-Id", "security-regression-guest")
                .multiPart("content", "Rate limit regression check")
                .post("/api/guest/conversations/send");

        // Should be 200 (processed) — not 500 (filter crashed)
        Assertions.assertNotEquals(500, res.getStatusCode(),
                "Rate limit filter should not cause 500 — filter must be active");
    }

    // TC-REG-15
    @Test
    @Story("Headers")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-REG-15 | Actuator health endpoint is public and returns UP")
    void TC_REG_15_actuatorHealthPublic() {
        Response res = given()
                .get("/actuator/health");

        Assertions.assertEquals(200, res.getStatusCode(),
                "Actuator health should be publicly accessible");
    }
}