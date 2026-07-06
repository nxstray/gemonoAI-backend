Feature: Passwordless Authentication — Magic Link
  As a user I want to sign in via a magic link sent to my email
  so that I don't need to remember a password

  Background:
    Given the backend is running on port 8020

  @TC-CUC-01 @critical
  Scenario: TC-CUC-01 | Send magic link returns 200 for valid email
    When I request a magic link for email "magictest@gemono.com"
    Then the response status should be 200
    And the response body should have success true

  @TC-CUC-02 @critical
  Scenario: TC-CUC-02 | Send magic link returns 400 for invalid email format
    When I request a magic link for email "not-an-email"
    Then the response status should be 400

  @TC-CUC-03 @critical
  Scenario: TC-CUC-03 | Verify with invalid token returns error
    When I verify magic link token "invalid-random-token-xyz"
    Then the response status should be 400
    And the response body should have success false

  @TC-CUC-04 @normal
  Scenario: TC-CUC-04 | Get profile without auth token returns 401
    When I request my profile without authentication
    Then the response status should be 401

  @TC-CUC-05 @normal
  Scenario: TC-CUC-05 | Send magic link with guest ID header is accepted
    Given I have a guest ID "cuc-guest-id-001"
    When I request a magic link for email "guestmerge@gemono.com" with guest ID
    Then the response status should be 200
    And the response body should have success true