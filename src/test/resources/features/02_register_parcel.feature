Feature: Register a parcel (UC2)
  As a partner, I want to register a parcel of a given size for a station and a recipient,
  so that a compartment is reserved for my courier.

  Scenario: A compatible compartment is reserved
    When the partner registers a "M" parcel at station "ORB-AVR-001" for 48 hours
    Then the parcel is "CREATED"
    And a "M" compartment is reserved for it
    And the partner receives a tracking code, a delivery code and a 6-digit drop-off code
    And the price is 6.00 EUR

  Scenario: The station has no free compartment of that size
    Given all "L" compartments of station "ORB-AVR-001" are reserved
    When the partner registers a "L" parcel at station "ORB-AVR-001" for 24 hours
    Then the request is rejected with status 409 and code "STATION_FULL"

  Scenario: An invalid lease duration is rejected
    When the partner registers a "S" parcel at station "ORB-AVR-001" for 36 hours
    Then the request is rejected with status 422 and code "INVALID_LEASE_DURATION"
