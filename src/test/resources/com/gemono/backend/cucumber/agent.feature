Feature: AI Agent Conversation
  As an authenticated user I want to send messages to the AI agent
  so that it can search the web and answer my research questions

  Background:
    Given the backend is running on port 8020
    And I am authenticated as "agent_test@gemono.com" with password "AgentPass123!"

  @TC-CUC-09 @critical
  Scenario: TC-CUC-09 | Authenticated user receives AI response
    When I send a chat message "What is the speed of light?"
    Then the response status should be 200
    And the response should contain an assistant message
    And the agent steps should not be empty

  @TC-CUC-10 @normal
  Scenario: TC-CUC-10 | Unauthenticated request to /conversations is rejected
    When I send a chat message without authentication
    Then the response status should be 401