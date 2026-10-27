package pt.ua.deti.tqs.orbit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.port.Notification;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;
import pt.ua.deti.tqs.orbit.service.view.TerminalResult;
import pt.ua.deti.tqs.orbit.support.InMemoryOrbit;
import pt.ua.deti.tqs.orbit.support.TestData;

/**
 * Drop-off and pickup codes: 6-digit PINs, valid while the parcel can use them, blocked for one hour after the third
 * wrong try at a station.
 *
 * <p>Each test uses its own fixture; the 3 and 5 seconds between tries are simulated with the {@code MutableClock}.</p>
 */
@DisplayName("Drop-off and pickup codes — format, validity, guessing protection")
class CodesFormatTest {

    private static final String SIX_DIGITS = "\\d{6}";

    private final InMemoryOrbit orbit = new InMemoryOrbit();

    private static String wrongCode(String right) {
        return right.equals("000000") ? "111111" : "000000";
    }

    private void dropOff(RegistrationResult r) {
        TerminalResult opened = orbit.terminal.startDropOff(InMemoryOrbit.STATION, r.dropOffCode());
        orbit.hardware.closeDoor(InMemoryOrbit.ref(orbit.parcel(r.trackingCode()).getCompartment()));
        orbit.terminal.confirmDropOff(InMemoryOrbit.STATION, opened.sessionToken());
    }

    private static void assertRejectedWith(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(OrbitException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    // ------------------------------------------------------------------ drop-off code

    @Test
    @DisplayName("Drop-off code: 6-digit PIN, valid until the drop-off deadline")
    void dropOffCodeIsA6DigitPinValidUntilTenWorkingDaysAfterTheAllocation() {
        // Arrange: two compartments allocated now (T0), both parcels collected by the courier
        RegistrationResult onTime = orbit.register("M-07");
        RegistrationResult late = orbit.register("M-11");
        orbit.parcelService.collect(onTime.deliveryCode(), null);
        orbit.parcelService.collect(late.deliveryCode(), null);

        // Act
        orbit.clock.setFixed(onTime.dropOffDeadline());
        TerminalResult atTheDeadline = orbit.terminal.startDropOff(InMemoryOrbit.STATION, onTime.dropOffCode());
        orbit.clock.setFixed(late.dropOffDeadline().plusSeconds(1));

        // Assert
        assertThat(onTime.dropOffCode()).matches(SIX_DIGITS);
        assertThat(onTime.dropOffDeadline()).isEqualTo(TestData.DROP_OFF_DEADLINE);
        assertThat(orbit.notifier.ofType(Notification.Type.REGISTRATION_CONFIRMED).get(0).body())
                .contains("until " + onTime.dropOffDeadline());
        assertThat(atTheDeadline.outcome()).isEqualTo("DOOR_OPENED");
        assertRejectedWith(() -> orbit.terminal.startDropOff(InMemoryOrbit.STATION, late.dropOffCode()),
                "DROP_OFF_DEADLINE_PASSED");
    }

    @Test
    @DisplayName("Drop-off code: the third wrong try blocks the terminal for one hour")
    void thirdWrongDropOffCodeBlocksTheTerminalForOneHour() {
        // Arrange
        RegistrationResult r = orbit.register(null);
        orbit.parcelService.collect(r.deliveryCode(), null);
        String wrong = wrongCode(r.dropOffCode());

        // Act: three wrong tries, 3 seconds apart
        assertRejectedWith(() -> orbit.terminal.startDropOff(InMemoryOrbit.STATION, wrong), "INVALID_CODE");
        orbit.clock.advance(Duration.ofSeconds(3));
        assertRejectedWith(() -> orbit.terminal.startDropOff(InMemoryOrbit.STATION, wrong), "INVALID_CODE");
        orbit.clock.advance(Duration.ofSeconds(3));
        Instant thirdTry = orbit.clock.instant();
        Throwable third = catchThrowable(
                () -> orbit.terminal.startDropOff(InMemoryOrbit.STATION, wrong));
        orbit.clock.advance(Duration.ofSeconds(5));

        // Assert: blocked for one hour, even for the right code 5 seconds later
        assertThat(third).isInstanceOfSatisfying(OrbitException.class, e -> {
            assertThat(e.getCode()).isEqualTo("TOO_MANY_ATTEMPTS");
            assertThat(e.getDetails()).containsEntry("lockedUntil", thirdTry.plus(Duration.ofHours(1)).toString());
        });
        assertRejectedWith(() -> orbit.terminal.startDropOff(InMemoryOrbit.STATION, r.dropOffCode()),
                "TOO_MANY_ATTEMPTS");
    }

    // ------------------------------------------------------------------ pickup code

    @Test
    @DisplayName("Pickup code: 6-digit PIN, valid until the parcel is RETURNED")
    void pickupCodeIsA6DigitPinThatStopsWorkingWhenTheParcelIsReturned() {
        // Arrange: parcel deposited in M-07
        RegistrationResult old = orbit.register("M-07");
        orbit.parcelService.collect(old.deliveryCode(), null);
        dropOff(old);
        String pickupCode = orbit.notifier.lastCode(Notification.Type.PICKUP_CODE).orElseThrow();

        // Act: the parcel expires, M-07 is reserved for another parcel, and that parcel is dropped off
        orbit.clock.advance(orbit.properties.rules().pickupWindow().plusSeconds(1));
        orbit.deadlines.processDeadlines();
        ParcelState afterDeadline = orbit.parcel(old.trackingCode()).getState();
        RegistrationResult next = orbit.register("M-07");
        ParcelState afterReallocation = orbit.parcel(old.trackingCode()).getState();
        orbit.parcelService.collect(next.deliveryCode(), null);
        dropOff(next);

        // Assert
        assertThat(pickupCode).matches(SIX_DIGITS);
        assertThat(afterDeadline).isEqualTo(ParcelState.EXPIRED);
        assertThat(afterReallocation).isEqualTo(ParcelState.TO_RETURN);
        assertThat(orbit.parcel(old.trackingCode()).getState()).isEqualTo(ParcelState.RETURNED);
        assertRejectedWith(() -> orbit.terminal.startPickup(InMemoryOrbit.STATION, pickupCode), "INVALID_CODE");
    }

    @Test
    @DisplayName("Pickup code: the third wrong try blocks the terminal for one hour")
    void thirdWrongPickupCodeBlocksTheTerminalForOneHour() {
        // Arrange
        RegistrationResult r = orbit.register(null);
        orbit.parcelService.collect(r.deliveryCode(), null);
        dropOff(r);
        String pickupCode = orbit.notifier.lastCode(Notification.Type.PICKUP_CODE).orElseThrow();
        String wrong = wrongCode(pickupCode);

        // Act: three wrong tries, 3 seconds apart
        assertRejectedWith(() -> orbit.terminal.startPickup(InMemoryOrbit.STATION, wrong), "INVALID_CODE");
        orbit.clock.advance(Duration.ofSeconds(3));
        assertRejectedWith(() -> orbit.terminal.startPickup(InMemoryOrbit.STATION, wrong), "INVALID_CODE");
        orbit.clock.advance(Duration.ofSeconds(3));
        Instant thirdTry = orbit.clock.instant();
        Throwable third = catchThrowable(
                () -> orbit.terminal.startPickup(InMemoryOrbit.STATION, wrong));
        orbit.clock.advance(Duration.ofSeconds(5));

        // Assert: blocked for one hour, even for the right code 5 seconds later
        assertThat(third).isInstanceOfSatisfying(OrbitException.class, e -> {
            assertThat(e.getCode()).isEqualTo("TOO_MANY_ATTEMPTS");
            assertThat(e.getDetails()).containsEntry("lockedUntil", thirdTry.plus(Duration.ofHours(1)).toString());
        });
        assertRejectedWith(() -> orbit.terminal.startPickup(InMemoryOrbit.STATION, pickupCode), "TOO_MANY_ATTEMPTS");
        assertThat(orbit.parcel(r.trackingCode()).getState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
    }
}
