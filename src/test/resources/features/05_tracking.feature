Feature: Track a parcel (US5)
  As a recipient or partner, I want to keep track of a parcel,
  so that I can get to know my packet's current delivery state, path and location.

  Scenario: Current state and timestamped history with actors
    Given a "M" parcel is registered at station "ORB-AVR-001" in compartment "M-07"
    And the courier collected the parcel
    When I track the parcel
    Then I should see "Your parcel is on its way."
    And the timeline should be, in this order:
      | Registration created |
      | Collected by courier |
      | Ready for pickup     |
      | Picked up            |
    And the stored history should be:
      | to         | actor   |
      | CREATED    | PARTNER |
      | ON_ITS_WAY | COURIER |

  Scenario: Unknown tracking code
    When I track the parcel "ORB-000000-0000"
    Then I should see a message saying "Invalid tracking code"
