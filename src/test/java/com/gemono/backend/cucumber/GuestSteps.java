package com.gemono.backend.cucumber;

import io.cucumber.java.en.*;
import io.qameta.allure.Step;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;

import static io.restassured.RestAssured.given;

@Tag("requires-groq")
public class GuestSteps {

    @When("I send a guest chat message {string} with my guest ID")
    @Step("Guest sends message: {0}")
    public void iSendGuestChatMessage(String content) {
        CommonSteps.lastResponse = given()
            .header("X-Guest-Id", CommonSteps.guestId)
            .multiPart("content", content)
            .post("/api/guest/conversations/send");
    }

    @When("I send a guest chat message without the X-Guest-Id header")
    @Step("Guest sends message without X-Guest-Id")
    public void iSendGuestMessageWithoutHeader() {
        CommonSteps.lastResponse = given()
            .multiPart("content", "Hello")
            .post("/api/guest/conversations/send");
    }

    @When("I request the guest conversation list with my guest ID")
    @Step("GET guest conversations for {0}")
    public void iRequestGuestConversationList() {
        CommonSteps.lastResponse = given()
            .header("X-Guest-Id", CommonSteps.guestId)
            .get("/api/guest/conversations");
    }

    @Then("the conversation list should not be empty")
    public void theConversationListShouldNotBeEmpty() {
        java.util.List<?> list = CommonSteps.lastResponse.jsonPath().getList("data");
        Assertions.assertNotNull(list);
        Assertions.assertFalse(list.isEmpty(), "Conversation list should not be empty");
    }
}