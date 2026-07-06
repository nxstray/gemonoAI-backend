package com.gemono.backend.cucumber;

import io.cucumber.java.Before;
import io.cucumber.java.en.*;
import io.qameta.allure.Step;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import com.gemono.backend.repository.MagicLinkTokenRepository;
import com.gemono.backend.model.MagicLinkToken;

import static io.restassured.RestAssured.given;

// Shared step definitions used across all feature files
public class CommonSteps {

    static int port = 8020;
    static String authToken = null;
    static String guestId = null;
    static Response lastResponse = null;

    @Autowired
    private MagicLinkTokenRepository magicLinkTokenRepository;

    @Before
    void setup() {
        RestAssured.baseURI = "http://localhost:" + port;
    }

    @Given("the backend is running on port {int}")
    public void theBackendIsRunning(int p) {
        port = p;
        RestAssured.baseURI = "http://localhost:" + port;
    }

    @Given("I have a guest ID {string}")
    public void iHaveAGuestId(String id) {
        guestId = id;
    }

    @Given("a registered user with email {string} and password {string}")
    @Step("Register user {0}")
    public void aRegisteredUserExists(String email, String password) {
        given()
            .contentType("application/json")
            .body("{\"email\":\"" + email + "\",\"fullName\":\"Test\",\"password\":\"" + password + "\"}")
            .post("/api/auth/register");
        // Ignore failure — user may already exist
    }

    @Given("I am authenticated as {string} with password {string}")
    @Step("Authenticate as {0} via Magic Link")
    public void iAmAuthenticatedAs(String email, String password) {
        
        // Steps 1: Request the backend to send a Magic Link for this email
        given()
            .contentType("application/json")
            .body("{\"email\":\"" + email + "\"}")
            .post("/api/auth/magic-link");

        // Steps 2: Get token that was generated in the database for this email
        String rawToken = magicLinkTokenRepository.findByTokenAndConsumedFalse(
            magicLinkTokenRepository.findAll().stream()
                .filter(t -> t.getEmail().equalsIgnoreCase(email) && !t.isConsumed())
                .map(MagicLinkToken::getToken)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Gagal generate Magic Link token di database untuk: " + email))
        ).get().getToken();

        // Steps 3: Exchange token for JWT trough the /api/auth/verify endpoint
        Response res = given()
            .contentType("application/json")
            .body("{\"token\":\"" + rawToken + "\"}")
            .post("/api/auth/verify");

        // get the JWT token from the response
        authToken = res.jsonPath().getString("data.token");
        
        // Make sure the authToken is not null or empty
        Assertions.assertNotNull(authToken, "Auth token must not be null after verifying magic link!");
    }

    @When("I check the health endpoint")
    @Step("GET /api/health")
    public void iCheckTheHealthEndpoint() {
        lastResponse = given().get("/api/health");
    }

    @Then("the response status should be {int}")
    public void theResponseStatusShouldBe(int status) {
        Assertions.assertEquals(status, lastResponse.getStatusCode());
    }

    @Then("the response body should have success true")
    public void theResponseBodyShouldHaveSuccessTrue() {
        Assertions.assertTrue(lastResponse.jsonPath().getBoolean("success"));
    }

    @Then("the response body should have success false")
    public void theResponseBodyShouldHaveSuccessFalse() {
        Assertions.assertFalse(lastResponse.jsonPath().getBoolean("success"));
    }

    @Then("the response body should contain a JWT token")
    public void theResponseBodyShouldContainJwtToken() {
        String token = lastResponse.jsonPath().getString("data.token");
        Assertions.assertNotNull(token);
        Assertions.assertFalse(token.isBlank());
    }

    @Then("the response should contain an assistant message")
    public void theResponseShouldContainAssistantMessage() {
        String role = lastResponse.jsonPath().getString("data.role");
        Assertions.assertEquals("assistant", role);
    }

    @Then("the agent steps should not be empty")
    public void theAgentStepsShouldNotBeEmpty() {
        java.util.List<?> steps = lastResponse.jsonPath().getList("data.agentSteps");
        Assertions.assertNotNull(steps);
        Assertions.assertFalse(steps.isEmpty());
    }
}