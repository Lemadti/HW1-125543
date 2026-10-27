Feature: Find a locker (US1)
  As a recipient, I want to enter my address and search for locker stations with free compartments
  on my location, so that I can find available stations near me.

  Scenario: Stations near an address are sorted by distance
    When I search for stations near "Rua de Coimbra 10, Aveiro"
    Then I should see "5 stations nearby"
    And the stations should be, in this order:
      | Fórum Aveiro           |
      | Universidade de Aveiro |
      | Estação de Aveiro      |
      | Glicínias Plaza        |
      | Ílhavo Centro          |
    And every station should show how many lockers are available

  Scenario: The recipient chooses the search radius
    When I search for stations near "Rua de Coimbra 10, Aveiro"
    And I choose a radius of 2 km
    Then I should see "4 stations within 2 km"
    And "Ílhavo Centro" should not be listed

  Scenario: Selecting a station shows its compartments
    When I search for stations near "Rua de Coimbra 10, Aveiro"
    And I select the station "Fórum Aveiro"
    Then I should see 16 compartments
    And compartment "S-06" should be shown as "out of service"

  Scenario: The geocoding provider is unavailable
    Given the geocoding provider is unavailable
    When I search for stations near "Rua de Coimbra 10, Aveiro"
    Then I should see a message saying "temporarily unavailable"
    And 5 stations should still be listed
