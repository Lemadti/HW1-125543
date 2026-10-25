Feature: Time-based rules
  Deadlines are evaluated against the application clock; the scenarios move the clock instead of waiting.

  Scenario: The lease ends before the courier collects the parcel
    Given a "M" parcel registered at station "ORB-AVR-001" for 24 hours
    When 24 hours pass
    Then the parcel is "CANCELLED"
    And the last transition was made by "SYSTEM"
    And the compartment is free again

  Scenario: The recipient does not collect the parcel within 15 days
    Given a "S" parcel waiting for pickup at station "ORB-AVR-001"
    When 14 days pass
    Then the parcel is "READY_FOR_PICKUP"
    When 1 day passes
    Then the parcel is "EXPIRED"
    And the recipient can pick up the parcel with the pickup code

  Scenario: An expired parcel is returned when its compartment is reused
    Given a "L" parcel waiting for pickup at station "ORB-AVR-001"
    And 15 days pass
    When another parcel is registered for the same compartment
    Then the expired parcel is "TO_RETURN"
    When the new parcel is dropped off in that compartment
    Then the expired parcel is "RETURNED"
