Feature: Find a locker (UC1)
  As a recipient, I want to enter my address and search for locker stations with free compartments,
  so that I can find available stations near me.

  Scenario: Stations near an address are sorted by distance
    When I search for stations near "Chmielna 30, Warsaw"
    Then the first station listed is "Centrum"
    And the stations are sorted by distance
    And every listed station shows its free compartments per size

  Scenario: Selecting a station shows its compartments
    When I open the station "ORB-WAW-014"
    Then I see 16 compartments
    And compartment "S-06" is "OUT_OF_SERVICE"

  Scenario: The geocoding provider is unavailable
    Given the geocoding provider is unavailable
    When I search for stations near "Chmielna 30, Warsaw"
    Then the search fails with status 503 and code "GEOCODER_UNAVAILABLE"
    And the list of all stations is still available
