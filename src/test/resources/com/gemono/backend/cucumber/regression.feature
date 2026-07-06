Feature: Regression — Core User Flows
  Verify critical user journeys have not regressed after code changes.
  These scenarios cover the most important paths a real user would take.

  Background:
    Given the backend is running on port 8020

  @TC-REG-BDD-01 @regression @critical
  Scenario: TC-REG-BDD-01 | Magic link flow — email accepted and verify endpoint rejects bad token
    When I request a magic link for email "bdd_regression@gemono.com"
    Then the response status should be 200
    And the response body should have success true
    When I verify magic link token "definitely-not-a-valid-token-bdd"
    Then the response status should be 400
    And the response body should have success false

  @TC-REG-BDD-02 @regression @critical
  Scenario: TC-REG-BDD-02 | Guest flow — list conversations with guest ID works end-to-end
    Given I have a guest ID "bdd-regression-guest-flow-001"
    When I request the guest conversation list with my guest ID
    Then the response status should be 200
    And the response body should have success true

  @TC-REG-BDD-03 @regression @critical
  Scenario: TC-REG-BDD-03 | Security — unauthenticated user cannot access protected conversation list
    When I send a chat message without authentication
    Then the response status should be 401

  @TC-REG-BDD-04 @regression @normal
  Scenario: TC-REG-BDD-04 | Health check — backend and Redis both report UP
    When I check the health endpoint
    Then the response status should be 200
    And the response body should have success true

  @TC-REG-BDD-05 @regression @normal
  Scenario: TC-REG-BDD-05 | Magic link validation — blank email returns 400
    When I request a magic link for email ""
    Then the response status should be 400
    And the response body should have success false