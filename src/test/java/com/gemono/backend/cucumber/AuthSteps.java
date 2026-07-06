package com.gemono.backend.cucumber;

import io.cucumber.java.en.*;
import io.qameta.allure.Step;

import static io.restassured.RestAssured.given;

public class AuthSteps {

    @When("I request a magic link for email {string}")
    @Step("POST /api/auth/magic-link for {0}")
    public void iRequestMagicLink(String email) {
        CommonSteps.lastResponse = given()
                .contentType("application/json")
                .body("{\"email\":\"" + email + "\"}")
                .post("/api/auth/magic-link");
    }

    @When("I request a magic link for email {string} with guest ID")
    @Step("POST /api/auth/magic-link for {0} with guest ID header")
    public void iRequestMagicLinkWithGuestId(String email) {
        CommonSteps.lastResponse = given()
                .contentType("application/json")
                .header("X-Guest-Id", CommonSteps.guestId)
                .body("{\"email\":\"" + email + "\"}")
                .post("/api/auth/magic-link");
    }

    @When("I verify magic link token {string}")
    @Step("POST /api/auth/verify with token {0}")
    public void iVerifyMagicLinkToken(String token) {
        CommonSteps.lastResponse = given()
                .contentType("application/json")
                .body("{\"token\":\"" + token + "\"}")
                .post("/api/auth/verify");
    }

    @When("I request my profile without authentication")
    @Step("GET /api/auth/me without token")
    public void iRequestProfileWithoutAuth() {
        CommonSteps.lastResponse = given()
                .get("/api/auth/me");
    }
}