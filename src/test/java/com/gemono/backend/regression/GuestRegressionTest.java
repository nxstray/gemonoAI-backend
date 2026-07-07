package com.gemono.backend.regression;

import io.qameta.allure.*;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Guest Flow Regression Tests — verify guest session behavior
 * has not regressed after code changes.
 *
 * Run: mvn test -Dgroups=regression
 */
@Epic("Regression")
@Feature("Guest Chat Flow")
@Tag("regression")
@DisplayName("Regression — Guest API Endpoints")
class GuestRegressionTest {

    private static final String GUEST_ID = "regression-guest-" + UUID.randomUUID();

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = System.getProperty("test.backend.url", "http://localhost:8020");
    }

    // TC-REG-07
    @Test
    @Story("Guest Session")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-REG-07 | Guest can list conversations without auth token")
    void TC_REG_07_guestListConversationsWithoutToken() {
        Response res = given()
                .header("X-Guest-Id", GUEST_ID)
                .get("/api/guest/conversations");

        Assertions.assertEquals(200, res.getStatusCode(),
                "Guest list should not require JWT");
        Assertions.assertTrue(res.jsonPath().getBoolean("success"));
    }

    // TC-REG-08
    @Test
    @Story("Guest Session")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("TC-REG-08 | Guest request without X-Guest-Id header returns 400")
    void TC_REG_08_guestWithoutHeaderReturns400() {
        Response res = given()
                .get("/api/guest/conversations");

        // Missing required header — should be 400, not 500
        Assertions.assertEquals(400, res.getStatusCode(),
                "Missing X-Guest-Id header should return 400");
    }

    // TC-REG-09
    @Test
    @Story("Guest Session")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-REG-09 | Guest cannot access authenticated conversations endpoint")
    void TC_REG_09_guestCannotAccessAuthConversations() {
        // /api/conversations requires JWT — guest should get 401
        Response res = given()
                .header("X-Guest-Id", GUEST_ID)
                .get("/api/conversations");

        Assertions.assertEquals(401, res.getStatusCode(),
                "Guest must not access /api/conversations — requires JWT");
    }

    // TC-REG-10
    @Test
    @Story("Guest Session")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("TC-REG-10 | Deleting non-existent guest conversation returns error, not 500")
    void TC_REG_10_deleteNonExistentGuestConversation() {
        Response res = given()
                .header("X-Guest-Id", GUEST_ID)
                .delete("/api/guest/conversations/99999999-9999-9999-9999-999999999999");

        Assertions.assertNotEquals(500, res.getStatusCode(),
                "Non-existent conversation delete should not cause 500");
    }
}