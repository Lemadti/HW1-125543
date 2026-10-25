Feature: Supervise a station (UC6)
  As an operator, I want to supervise a station, so that I can handle expired parcels
  and faulty compartments.

  Scenario: Register the removal of an expired parcel
    Given a "S" parcel waiting for pickup at station "ORB-WAW-014"
    And 15 days pass
    When the operator lists the "EXPIRED" parcels of station "ORB-WAW-014"
    Then the parcel is in the list
    When the operator registers the removal of the parcel
    Then the parcel is "RETURNED"
    And the last transition was made by "OPERATOR"

  Scenario: Put a faulty compartment out of service
    When the operator puts compartment "M-15" of station "ORB-WAW-014" out of service
    Then compartment "M-15" is "OUT_OF_SERVICE"
    And no parcel can be registered in compartment "M-15" of station "ORB-WAW-014"
