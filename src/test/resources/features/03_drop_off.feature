Feature: Drop off at the locker terminal (UC3)
  As a courier, I want to enter the drop-off code to open the reserved compartment,
  so that I can store the parcel safely.

  Background:
    Given a "M" parcel registered at station "ORB-WAW-014"

  Scenario: Successful drop-off notifies the recipient
    Given the courier collected the parcel
    When the courier enters the drop-off code at the terminal
    Then the compartment opens
    When the courier closes the door
    Then the parcel is "READY_FOR_PICKUP"
    And the recipient receives a 6-digit pickup code different from the drop-off code

  Scenario: A parcel that was not collected cannot be dropped off
    When the courier enters the drop-off code at the terminal
    Then the request is rejected with status 409 and code "INVALID_TRANSITION"

  Scenario: The compartment door does not open (hardware failure)
    Given the courier collected the parcel
    And the door of the reserved compartment will not open
    When the courier enters the drop-off code at the terminal
    Then the terminal reports "DOOR_DID_NOT_OPEN" with a replacement available
    And the faulty compartment is out of service
    And the operator is notified of the malfunction
    When the courier asks for another compartment
    Then the parcel is moved to another "M" compartment
    And the sender receives a new drop-off code that opens the new compartment

  Scenario: The station controller does not respond
    Given the courier collected the parcel
    And the station controller is not responding
    When the courier enters the drop-off code at the terminal
    Then the request is rejected with status 503 and code "HARDWARE_UNAVAILABLE"
    And the parcel is "ON_ITS_WAY"
