package com.gemono.backend.regression;

import io.qameta.allure.*;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;

@Epic("Regression")
@Feature("Authentication Flow")
@Tag("regression")
@DisplayName("Regression — Auth API Endpoints")
class AuthRegressionTest {

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = System.getProperty("test.backend.url", "http://localhost:8020");
        RestAssured.config = io.restassured.RestAssured.config()
                .redirect(io.restassured.config.RedirectConfig.redirectConfig().followRedirects(false));
    }

    // TC-REG-01 — hit Resend
    @Test
    @Tag("requires-external")
    @Story("Magic Link")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-REG-01 | Magic link endpoint still accepts valid email format")
    void TC_REG_01_magicLinkAcceptsValidEmail() {
        Response res = given()
                .contentType("application/json")
                .body("{\"email\":\"afwanapriansyah@gmail.com\"}")
                .post("/api/auth/magic-link");

        Assertions.assertEquals(200, res.getStatusCode(),
                "Magic link endpoint should return 200 for valid email");
        Assertions.assertTrue(res.jsonPath().getBoolean("success"));
    }

    // TC-REG-02
    @Test
    @Story("Magic Link")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-REG-02 | Magic link rejects malformed email — validation still works")
    void TC_REG_02_magicLinkRejectsMalformedEmail() {
        Response res = given()
                .contentType("application/json")
                .body("{\"email\":\"not-an-email\"}")
                .post("/api/auth/magic-link");

        Assertions.assertEquals(400, res.getStatusCode(),
                "Validation should still reject malformed email");
        Assertions.assertFalse(res.jsonPath().getBoolean("success"));
    }

    // TC-REG-03
    @Test
    @Story("Magic Link")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-REG-03 | Verify endpoint rejects invalid token — not leaking 500")
    void TC_REG_03_verifyRejectsInvalidToken() {
        Response res = given()
                .contentType("application/json")
                .body("{\"token\":\"regression-invalid-token-xyz\"}")
                .post("/api/auth/verify");

        Assertions.assertEquals(400, res.getStatusCode(),
                "Invalid token should return 400, not 500");
        Assertions.assertFalse(res.jsonPath().getBoolean("success"));
        Assertions.assertNotNull(res.jsonPath().getString("error"));
    }

    // TC-REG-04
    @Test
    @Story("Security")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-REG-04 | JWT-protected endpoints reject unauthenticated requests")
    void TC_REG_04_protectedEndpointRequiresJwt() {
        // No token — expect 401 or 403, never 200 or 500
        Response noAuth = given().get("/api/auth/me");
        Assertions.assertTrue(
                noAuth.getStatusCode() == 401 || noAuth.getStatusCode() == 403,
                "GET /me without token should return 401 or 403, got: " + noAuth.getStatusCode()
        );

        // Invalid token — same expectation
        Response badToken = given()
                .header("Authorization", "Bearer invalid.jwt.token")
                .get("/api/auth/me");
        Assertions.assertTrue(
                badToken.getStatusCode() == 401 || badToken.getStatusCode() == 403,
                "GET /me with invalid token should return 401 or 403, got: " + badToken.getStatusCode()
        );
    }

    // TC-REG-05 — hit Resend
    @Test
    @Tag("requires-external")
    @Story("Magic Link")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-REG-05 | Magic link with X-Guest-Id header still accepted")
    void TC_REG_05_magicLinkWithGuestIdStillWorks() {
        Response res = given()
                .contentType("application/json")
                .header("X-Guest-Id", "regression-guest-id-001")
                .body("{\"email\":\"afwanapriansyah@gmail.com\"}")
                .post("/api/auth/magic-link");

        Assertions.assertEquals(200, res.getStatusCode(),
                "Magic link with X-Guest-Id header should still return 200");
    }
}