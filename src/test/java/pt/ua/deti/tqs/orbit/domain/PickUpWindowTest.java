package pt.ua.deti.tqs.orbit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.port.Notification;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;
import pt.ua.deti.tqs.orbit.service.view.TerminalResult;
import pt.ua.deti.tqs.orbit.support.InMemoryOrbit;

/**
 * Pickup deadline rule: pickup is allowed for the 15 days after the drop-off. Afterwards the parcel is EXPIRED but can
 * still be picked up (also when TO_RETURN) until it is RETURNED, i.e. taken out by the courier who drops the next
 * parcel in that compartment.
 *
 * <p>Each test uses its own fixture: a parcel deposited in M-07 one hour after the allocation. Time moves through the
 * {@code MutableClock}, and the deadline job runs explicitly, as the scheduler would.</p>
 */
@DisplayName("Pickup deadline — 15 days after the drop-off")
class PickUpWindowTest {

    private final InMemoryOrbit orbit = new InMemoryOrbit();
    private RegistrationResult registered;
    private String pickupCode;
    private Instant droppedOffAt;

    @BeforeEach
    void depositAParcel() {
        registered = orbit.register("M-07");
        orbit.parcelService.collect(registered.deliveryCode(), null);
        orbit.clock.advance(Duration.ofHours(1));
        droppedOffAt = orbit.clock.instant();
        dropOff(registered);
        pickupCode = orbit.notifier.lastCode(Notification.Type.PICKUP_CODE).orElseThrow();
    }

    private void dropOff(RegistrationResult r) {
        TerminalResult opened = orbit.terminal.startDropOff(InMemoryOrbit.STATION, r.dropOffCode());
        orbit.hardware.closeDoor(InMemoryOrbit.ref(orbit.m07));
        orbit.terminal.confirmDropOff(InMemoryOrbit.STATION, opened.sessionToken());
    }

    private Parcel parcel() {
        return orbit.parcel(registered.trackingCode());
    }

    /** Moves past the 15-day window and runs the deadline job: the parcel becomes EXPIRED. */
    private void letThePickupWindowEnd() {
        orbit.clock.setFixed(droppedOffAt.plus(Duration.ofDays(15)).plusSeconds(1));
        orbit.deadlines.processDeadlines();
    }

    @Test
    @DisplayName("Pickup deadline: inside the 15 days, the pickup is allowed")
    void pickupBeforeFifteenDaysAfterTheDropOffIsAllowed() {
        // Arrange
        orbit.clock.setFixed(droppedOffAt.plus(Duration.ofDays(7)));

        // Act
        TerminalResult result = orbit.terminal.startPickup(InMemoryOrbit.STATION, pickupCode);

        // Assert
        assertThat(result.outcome()).isEqualTo("DOOR_OPENED");
        assertThat(parcel().getState()).isEqualTo(ParcelState.PICKED_UP);
    }

    @Test
    @DisplayName("Pickup deadline: boundary, exactly 15 days is still allowed")
    void pickupExactlyFifteenDaysAfterTheDropOffIsAllowed() {
        // Arrange
        orbit.clock.setFixed(droppedOffAt.plus(Duration.ofDays(15)));
        orbit.deadlines.processDeadlines();

        // Act
        TerminalResult result = orbit.terminal.startPickup(InMemoryOrbit.STATION, pickupCode);

        // Assert
        assertThat(result.outcome()).isEqualTo("DOOR_OPENED");
        assertThat(parcel().getState()).isEqualTo(ParcelState.PICKED_UP);
    }

    @Test
    @DisplayName("Pickup deadline: an EXPIRED parcel can still be picked up")
    void pickupAfterFifteenDaysOfAnExpiredParcelIsAllowed() {
        // Arrange
        letThePickupWindowEnd();
        assertThat(parcel().getState()).isEqualTo(ParcelState.EXPIRED);

        // Act
        TerminalResult result = orbit.terminal.startPickup(InMemoryOrbit.STATION, pickupCode);

        // Assert
        assertThat(result.outcome()).isEqualTo("DOOR_OPENED");
        assertThat(parcel().getState()).isEqualTo(ParcelState.PICKED_UP);
    }

    @Test
    @DisplayName("Pickup deadline: a TO_RETURN parcel can still be picked up")
    void pickupAfterFifteenDaysOfAParcelToReturnIsAllowed() {
        // Arrange: expired, then M-07 is reserved for another parcel
        letThePickupWindowEnd();
        orbit.register("M-07");
        assertThat(parcel().getState()).isEqualTo(ParcelState.TO_RETURN);

        // Act
        TerminalResult result = orbit.terminal.startPickup(InMemoryOrbit.STATION, pickupCode);

        // Assert
        assertThat(result.outcome()).isEqualTo("DOOR_OPENED");
        assertThat(parcel().getState()).isEqualTo(ParcelState.PICKED_UP);
    }

    @Test
    @DisplayName("Pickup deadline: a RETURNED parcel can no longer be picked up")
    void pickupAfterFifteenDaysOfAReturnedParcelIsNotAllowed() {
        // Arrange: expired, M-07 reserved for another parcel, and that parcel was dropped off
        letThePickupWindowEnd();
        RegistrationResult next = orbit.register("M-07");
        orbit.parcelService.collect(next.deliveryCode(), null);
        dropOff(next);
        assertThat(parcel().getState()).isEqualTo(ParcelState.RETURNED);

        // Act + Assert
        assertThatThrownBy(() -> orbit.terminal.startPickup(InMemoryOrbit.STATION, pickupCode))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_CODE"));
        assertThat(parcel().getState()).isEqualTo(ParcelState.RETURNED);
    }
}
