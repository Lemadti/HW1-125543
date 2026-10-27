Feature: Pick up at the locker terminal (US4)
  As a recipient, I want to enter the pick up code and open my compartment,
  so that I can pick my packet up without the risk of it being stolen.

  Background:
    Given a "S" parcel is waiting for pickup at station "ORB-AVR-001" in compartment "S-01"

  Scenario: Valid pickup code
    When the recipient enters the pickup code at the terminal
    Then the terminal should show "Compartment S-01 is open"
    When the recipient closes the door
    Then the terminal should show "Enjoy your parcel!"
    And the parcel should be stored as "PICKED_UP"
    And compartment "S-01" should be free again

  Scenario: The pickup code can only be used once
    Given the recipient already picked up the parcel
    When the recipient enters the pickup code at the terminal
    Then I should see a message saying "This code is not valid at this station. 2 attempt(s) left."

  Scenario: Code entry is blocked after three wrong codes
    When someone enters 3 wrong pickup codes at the terminal
    Then the terminal should show "Code entry blocked"
    When the recipient enters the pickup code at the terminal
    Then the terminal should show "Code entry blocked"
    When 1 hour passes
    And the recipient enters the pickup code at the terminal
    Then the terminal should show "Compartment S-01 is open"
