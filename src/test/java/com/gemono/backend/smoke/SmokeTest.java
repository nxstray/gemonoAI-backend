package com.gemono.backend.smoke;

import io.qameta.allure.*;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;

/**
 * Smoke Tests — quick sanity check that the application is alive.
 * Run these first before any other test suite.
 * Target: all pass within 10 seconds total.
 *
 * Run: mvn test -Dgroups=smoke
 */
@Epic("Smoke")
@Feature("Application Sanity Check")
@Tag("smoke")
@DisplayName("Smoke Tests — Application Health")
class SmokeTest {

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://localhost:8020";
    }

    // TC-SMK-01
    @Test
    @Story("Health")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("TC-SMK-01 | Backend is reachable and returns 200")
    void TC_SMK_01_backendIsReachable() {
        Response res = given()
                .get("/api/health");

        Assertions.assertEquals(200, res.getStatusCode(),
                "Backend should be reachable at /api/health");
        Assertions.assertTrue(res.jsonPath().getBoolean("success"),
                "Health response success should be true");
    }

    // TC-SMK-02
    @Test
    @Story("Health")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("TC-SMK-02 | Redis is connected and responding")
    void TC_SMK_02_redisIsConnected() {
        Response res = given()
                .get("/api/health/redis");

        Assertions.assertEquals(200, res.getStatusCode(),
                "Redis health endpoint should return 200");

        String redisStatus = res.jsonPath().getString("data.redis");
        Assertions.assertEquals("UP", redisStatus,
                "Redis should be UP");
    }

    // TC-SMK-03
    @Test
    @Story("Auth")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("TC-SMK-03 | Auth endpoint is accessible (magic-link returns 400 for empty body, not 500)")
    void TC_SMK_03_authEndpointAccessible() {
        // Empty body → validation error 400, not 500
        // If it returns 500, Spring context failed to load
        Response res = given()
                .contentType("application/json")
                .body("{}")
                .post("/api/auth/magic-link");

        Assertions.assertNotEquals(500, res.getStatusCode(),
                "Auth endpoint should not return 500 — Spring context must be healthy");
        Assertions.assertEquals(400, res.getStatusCode(),
                "Empty email should return 400 validation error");
    }

    // TC-SMK-04
    @Test
    @Story("Security")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("TC-SMK-04 | Protected endpoint returns 401 without token (not 500)")
    void TC_SMK_04_protectedEndpointReturns401() {
        Response res = given()
                .get("/api/auth/me");

        Assertions.assertEquals(401, res.getStatusCode(),
                "Protected endpoint should return 401 without JWT, not 500");
    }

    // TC-SMK-05
    @Test
    @Story("Guest")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("TC-SMK-05 | Guest conversation list endpoint is reachable")
    void TC_SMK_05_guestEndpointReachable() {
        Response res = given()
                .header("X-Guest-Id", "smoke-test-guest-id")
                .get("/api/guest/conversations");

        Assertions.assertEquals(200, res.getStatusCode(),
                "Guest conversations endpoint should return 200");
    }

    // TC-SMK-06
    @Test
    @Tag("prod-unsafe")
    @Story("Swagger")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-SMK-06 | Swagger UI is accessible")
    void TC_SMK_06_swaggerUiAccessible() {
        Response res = given()
                .get("/swagger-ui/index.html");

        Assertions.assertEquals(200, res.getStatusCode(),
                "Swagger UI should be accessible at /swagger-ui/index.html");
    }

    // TC-SMK-07
    @Test
    @Tag("prod-unsafe") 
    @Story("Swagger")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-SMK-07 | OpenAPI spec endpoint is accessible (for Postman import)")
    void TC_SMK_07_openApiSpecAccessible() {
        Response res = given()
                .get("/v3/api-docs");

        Assertions.assertEquals(200, res.getStatusCode(),
                "OpenAPI spec should be accessible at /v3/api-docs");

        // Basic structure check
        String title = res.jsonPath().getString("info.title");
        Assertions.assertNotNull(title, "OpenAPI spec should have a title");
    }
}