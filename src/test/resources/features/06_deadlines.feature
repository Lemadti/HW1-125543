Feature: Time-based rules
  Deadlines are evaluated against the application clock; the scenarios move the clock instead of waiting.

  Scenario: The drop-off deadline passes before the courier collects the parcel
    Given a "M" parcel is registered at station "ORB-AVR-001" in compartment "M-07"
    When the drop-off deadline passes
    Then the parcel should be stored as "CANCELLED" by "SYSTEM"
    And compartment "M-07" should be free again
    When I track the parcel
    Then I should see "This parcel was cancelled."

  Scenario: A collected parcel that is not dropped off on time is cancelled
    Given a "M" parcel is registered at station "ORB-AVR-001" in compartment "M-07"
    And the courier collected the parcel
    When the drop-off deadline passes
    Then the parcel should be stored as "CANCELLED" by "SYSTEM"
    When the courier enters the drop-off PIN at the terminal
    Then I should see a message saying "This code is not valid at this station."

  Scenario: The recipient does not collect the parcel within 15 days
    Given a "S" parcel is waiting for pickup at station "ORB-AVR-001" in compartment "S-01"
    When 14 days pass
    Then the parcel should be stored as "READY_FOR_PICKUP"
    When 1 day passes
    Then the parcel should be stored as "EXPIRED"
    When the recipient enters the pickup code at the terminal
    Then the terminal should show "Compartment S-01 is open"

  Scenario: An expired parcel is returned when its compartment is reused
    Given a "L" parcel is waiting for pickup at station "ORB-AVR-001" in compartment "L-04"
    And 15 days pass
    When another parcel is registered for compartment "L-04"
    Then the expired parcel should be stored as "TO_RETURN"
    When the courier drops the new parcel off at the terminal
    Then the terminal should show "Parcel stored"
    And the expired parcel should be stored as "RETURNED"
