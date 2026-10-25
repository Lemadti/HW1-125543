Feature: Track a parcel (UC5)
  As a recipient or partner, I want to track a parcel with its tracking code,
  so that I know its current state and history.

  Scenario: Timestamped history with actors
    Given a "M" parcel registered at station "ORB-AVR-001"
    And the courier collected the parcel
    When I track the parcel
    Then the current state is "ON_ITS_WAY"
    And the history is:
      | to         | actor   |
      | CREATED    | PARTNER |
      | ON_ITS_WAY | COURIER |

  Scenario: Unknown tracking code
    When I track the parcel "ORB-000000-0000"
    Then the request is rejected with status 404 and code "PARCEL_NOT_FOUND"
