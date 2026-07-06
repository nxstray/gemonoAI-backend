package com.gemono.backend.cucumber;

import io.cucumber.java.en.*;
import io.qameta.allure.Step;
import org.junit.jupiter.api.Tag;

import static io.restassured.RestAssured.given;

@Tag("requires-groq")
public class AgentSteps {

    @When("I send a chat message {string}")
    @Step("Authenticated user sends: {0}")
    public void iSendChatMessage(String content) {
        CommonSteps.lastResponse = given()
            .header("Authorization", "Bearer " + CommonSteps.authToken)
            .multiPart("content", content)
            .post("/api/conversations/send");
    }

    @When("I send a chat message without authentication")
    @Step("Unauthenticated chat request")
    public void iSendChatMessageWithoutAuth() {
        CommonSteps.lastResponse = given()
            .multiPart("content", "Hello")
            .post("/api/conversations/send");
    }
}