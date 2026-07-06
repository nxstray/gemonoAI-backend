Feature: Guest Chat Session
  As an unauthenticated visitor I want to chat with Gemono
  so that I can try the AI agent before creating an account

  Background:
    Given the backend is running on port 8020
    And I have a guest ID "test-guest-uuid-abc123"

  @TC-CUC-06 @critical
  Scenario: TC-CUC-06 | Guest can start a new conversation
    When I send a guest chat message "What is machine learning?" with my guest ID
    Then the response status should be 200
    And the response body should have success true
    And the response should contain an assistant message

  @TC-CUC-07 @normal
  Scenario: TC-CUC-07 | Guest conversation is listed after first message
    When I send a guest chat message "Explain neural networks" with my guest ID
    And I request the guest conversation list with my guest ID
    Then the response status should be 200
    And the conversation list should not be empty

  @TC-CUC-08 @normal
  Scenario: TC-CUC-08 | Guest request without X-Guest-Id header is rejected
    When I send a guest chat message without the X-Guest-Id header
    Then the response status should be 400