package pt.ua.deti.tqs.orbit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;
import pt.ua.deti.tqs.orbit.service.view.TerminalResult;
import pt.ua.deti.tqs.orbit.support.InMemoryOrbit;
import pt.ua.deti.tqs.orbit.support.TestData;

/**
 * Drop-off deadline rule: drop-off is allowed for 10 working days after the allocation (the registration).
 *
 * <p>The compartment is allocated at {@link TestData#T0}, Saturday 24 October 2026 09:00 in Lisbon, so the window
 * ends on Friday 6 November 2026 09:00 ({@link TestData#DROP_OFF_DEADLINE}, worked out by hand: weekends are
 * skipped). Each test uses its own fixture and moves the {@code MutableClock} instead of waiting.</p>
 */
@DisplayName("Drop-off deadline — 10 working days after the allocation")
class DropOffWindowTest {

    private final InMemoryOrbit orbit = new InMemoryOrbit();

    /** The courier enters the drop-off code, places the parcel and closes the door. */
    private TerminalResult dropOff(RegistrationResult r) {
        TerminalResult opened = orbit.terminal.startDropOff(InMemoryOrbit.STATION, r.dropOffCode());
        orbit.hardware.closeDoor(InMemoryOrbit.ref(orbit.parcel(r.trackingCode()).getCompartment()));
        return orbit.terminal.confirmDropOff(InMemoryOrbit.STATION, opened.sessionToken());
    }

    @Test
    @DisplayName("Drop-off deadline: a parcel not yet collected cannot be dropped off")
    void dropOffAtTheTimeOfTheAllocationIsRejected() {
        // Arrange: the compartment is allocated now; the courier has not collected the parcel yet
        RegistrationResult r = orbit.register(null);

        // Act + Assert
        assertThatThrownBy(() -> dropOff(r)).isInstanceOf(InvalidTransitionException.class);
        assertThat(orbit.parcel(r.trackingCode()).getState()).isEqualTo(ParcelState.CREATED);
    }

    @Test
    @DisplayName("Drop-off deadline: inside the window, the drop-off is allowed")
    void dropOffAfterTheAllocationAndBeforeTenWorkingDaysIsAllowed() {
        // Arrange: allocated on Saturday, dropped off on Wednesday 28 October (3rd working day)
        RegistrationResult r = orbit.register(null);
        orbit.parcelService.collect(r.deliveryCode(), null);
        orbit.clock.setFixed(TestData.T0.plus(Duration.ofDays(4)));

        // Act
        TerminalResult result = dropOff(r);

        // Assert
        assertThat(result.outcome()).isEqualTo("DEPOSITED");
        assertThat(orbit.parcel(r.trackingCode()).getState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
    }

    @Test
    @DisplayName("Drop-off deadline: boundary, exactly 10 working days is still allowed")
    void dropOffExactlyTenWorkingDaysAfterTheAllocationIsAllowed() {
        // Arrange
        RegistrationResult r = orbit.register(null);
        orbit.parcelService.collect(r.deliveryCode(), null);
        orbit.clock.setFixed(TestData.DROP_OFF_DEADLINE);

        // Act
        TerminalResult result = dropOff(r);

        // Assert
        assertThat(r.dropOffDeadline()).isEqualTo(TestData.DROP_OFF_DEADLINE);
        assertThat(result.outcome()).isEqualTo("DEPOSITED");
        assertThat(orbit.parcel(r.trackingCode()).getState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
    }

    @Test
    @DisplayName("Drop-off deadline: one second after the window, the drop-off is refused")
    void dropOffAfterTenWorkingDaysAfterTheAllocationIsRejected() {
        // Arrange: collected in time, but the courier arrives one second after the window ends (before the deadline
        // job ran)
        RegistrationResult r = orbit.register(null);
        orbit.parcelService.collect(r.deliveryCode(), null);
        Instant late = TestData.DROP_OFF_DEADLINE.plusSeconds(1);
        orbit.clock.setFixed(late);

        // Act + Assert
        assertThatThrownBy(() -> dropOff(r)).isInstanceOfSatisfying(OrbitException.class,
                e -> assertThat(e.getCode()).isEqualTo("DROP_OFF_DEADLINE_PASSED"));
        assertThat(orbit.parcel(r.trackingCode()).getState()).isEqualTo(ParcelState.CANCELLED);
    }
}
