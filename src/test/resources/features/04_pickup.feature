Feature: Pick up at the locker terminal (UC4)
  As a recipient, I want to enter the pickup code and open my compartment,
  so that I can collect my parcel without the risk of it being stolen.

  Background:
    Given a "S" parcel waiting for pickup at station "ORB-WAW-014"

  Scenario: Valid pickup code
    When the recipient enters the pickup code at the terminal
    Then the compartment opens
    And the parcel is "PICKED_UP"
    And the compartment is free again

  Scenario: The pickup code can only be used once
    Given the recipient already picked up the parcel
    When the recipient enters the pickup code at the terminal
    Then the request is rejected with status 422 and code "INVALID_CODE"

  Scenario: Code entry is blocked after three wrong codes
    When someone enters 3 wrong pickup codes at the terminal
    Then the terminal blocks code entry with status 429
    And even the correct pickup code is rejected
    When 1 hour passes
    Then the recipient can pick up the parcel with the pickup code
