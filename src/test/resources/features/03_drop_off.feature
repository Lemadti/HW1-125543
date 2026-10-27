Feature: Drop off at the locker terminal (US3)
  As a courier, I want to enter the drop-off code to open the reserved compartment,
  so that I can drop the packet off, close the door and let it stored safely.

  Background:
    Given a "M" parcel is registered at station "ORB-AVR-001" in compartment "M-07"

  Scenario: Successful drop-off notifies the recipient
    When the courier confirms the collection of the parcel on the courier page
    Then I should see a message saying "is on its way to Fórum Aveiro (M-07)"
    And the parcel should be stored as "ON_ITS_WAY"
    When the courier enters the drop-off PIN at the terminal
    Then the terminal should show "Compartment M-07 is open"
    When the courier closes the door
    Then the terminal should show "Parcel stored"
    And the parcel should be stored as "READY_FOR_PICKUP"
    And the recipient should receive a 6-digit pickup code different from the drop-off PIN

  Scenario: A parcel that was not collected cannot be dropped off
    When the courier enters the drop-off PIN at the terminal
    Then the terminal should show "This parcel cannot be dropped off yet"
    And the parcel should be stored as "CREATED"

  Scenario: The compartment door does not open (hardware failure)
    Given the courier collected the parcel
    And the door of compartment "M-07" will not open
    When the courier enters the drop-off PIN at the terminal
    Then the terminal should show "The compartment did not open"
    And compartment "M-07" should be stored as "OUT_OF_SERVICE"
    And the operator should be notified of a "DOOR_DID_NOT_OPEN" in compartment "M-07"
    When the courier asks for another compartment
    Then the terminal should show "New compartment: M-03"
    And the new drop-off PIN sent to the sender should open compartment "M-03"

  Scenario: The locker does not respond
    Given the courier collected the parcel
    And the locker is not responding
    When the courier enters the drop-off PIN at the terminal
    Then the terminal should show "The locker is not responding"
    And I should see a message saying "+351 234 100 200"
    And the parcel should be stored as "ON_ITS_WAY"
