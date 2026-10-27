Feature: Supervise a station (US6)
  As an operator, I want to supervise a station, so that I can see expired parcels,
  register their removal or put a faulty compartment out of service.

  Scenario: Register the removal of an expired parcel
    Given a "S" parcel is waiting for pickup at station "ORB-AVR-001" in compartment "S-05"
    And 15 days pass
    When the operator shows the "Expired" parcels of station "ORB-AVR-001"
    Then the parcel should be listed
    When the operator registers the removal of the parcel
    Then I should see a message saying "registered (RETURNED)"
    And the parcel should be stored as "RETURNED" by "OPERATOR"

  Scenario: Put a faulty compartment out of service
    When the operator puts compartment "M-15" of station "ORB-AVR-001" out of service because "Faulty lock"
    Then I should see "M-15" in the malfunction reports
    And compartment "M-15" should be stored as "OUT_OF_SERVICE"
    And I am registering a "Medium" parcel at station "Fórum Aveiro"
    And compartment "M-15" should be shown as "out of service"
