Feature: Register a parcel (US2)
  As a partner, I want to register a parcel of a specific size for a chosen station and recipient contact,
  so that I can have a reserved compartment for my courier to drop the recipient's packet.

  Scenario: The partner reserves a free compartment of the parcel size
    Given I am registering a "Medium" parcel at station "Fórum Aveiro"
    When I choose compartment "M-11"
    And I fill in the sender and recipient contacts and accept the terms
    Then I should see "3.00 EUR"
    When I submit the registration
    Then I should see a message saying "Compartment M-11 reserved at Fórum Aveiro"
    And I should see a tracking code, a delivery code and a 6-digit drop-off PIN
    And the parcel should be stored as "CREATED" in compartment "M-11"
    And the drop-off deadline should be 10 working days after the registration

  Scenario: A reserved compartment cannot be chosen
    Given compartment "M-07" of station "ORB-AVR-001" is reserved
    And I am registering a "Medium" parcel at station "Fórum Aveiro"
    When I choose compartment "M-07"
    Then I should see "M-07 · Reserved"
    And I should not be able to continue the registration

  Scenario: A station without a free compartment of that size is not offered
    Given all "L" compartments of station "ORB-AVR-001" are reserved
    When I look for a "Large" compartment near "Rua de Coimbra 10, Aveiro"
    Then I should see "4 matching stations nearby"
    And "Fórum Aveiro" should not be listed
