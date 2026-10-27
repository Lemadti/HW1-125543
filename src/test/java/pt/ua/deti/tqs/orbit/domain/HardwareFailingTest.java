package pt.ua.deti.tqs.orbit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.adapter.hardware.FakeLockerHardware.Fault;
import pt.ua.deti.tqs.orbit.port.Notification;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;
import pt.ua.deti.tqs.orbit.service.view.TerminalResult;
import pt.ua.deti.tqs.orbit.support.InMemoryOrbit;

/**
 * Hardware failure rules of the report (oracle: their value/rationale):
 * <ul>
 *   <li><b>The door of the locker doesn't open</b> and <b>the door of the locker doesn't close</b>: the user can ask
 *       in the interface to replace the locker; if another locker of the same size is available, a different code is
 *       sent, entering it opens the new locker, and the malfunction is reported to the operator automatically.</li>
 *   <li><b>The interface doesn't respond</b>: the user is advised to call the station's assistance number.</li>
 * </ul>
 *
 * <p>Each test uses its own fixture: a medium parcel reserved in M-07 and collected by the courier; M-11 is the other
 * medium compartment and S-01 a small one. Failures are injected in the fake locker hardware.</p>
 */
@DisplayName("Hardware failures — door doesn't open, doesn't close, interface doesn't respond")
class HardwareFailingTest {

    private static final String STATION = InMemoryOrbit.STATION;

    private final InMemoryOrbit orbit = new InMemoryOrbit();

    private RegistrationResult reserveAndCollectInM07() {
        RegistrationResult r = orbit.register("M-07");
        orbit.parcelService.collect(r.deliveryCode(), null);
        return r;
    }

    private Parcel parcel(RegistrationResult r) {
        return orbit.parcel(r.trackingCode());
    }

    private String replacementCode() {
        return orbit.notifier.lastCode(Notification.Type.REPLACEMENT_DROP_OFF_CODE).orElseThrow();
    }

    /** The courier enters the code, the door does not close, and the terminal reports it. */
    private TerminalResult dropOffWithADoorThatDoesNotClose(RegistrationResult r) {
        orbit.hardware.inject(InMemoryOrbit.ref(orbit.m07), Fault.DOOR_WONT_CLOSE);
        TerminalResult opened = orbit.terminal.startDropOff(STATION, r.dropOffCode());
        orbit.hardware.closeDoor(InMemoryOrbit.ref(orbit.m07));
        return orbit.terminal.confirmDropOff(STATION, opened.sessionToken());
    }

    // ------------------------------------------------------------------ the door of the locker doesn't open

    @Test
    @DisplayName("Door doesn't open: the user can ask for another locker of the same size")
    void doorThatDoesNotOpenLetsTheUserAskForAnotherLockerWhenOneOfTheSameSizeIsFree() {
        // Arrange
        RegistrationResult r = reserveAndCollectInM07();
        orbit.hardware.inject(InMemoryOrbit.ref(orbit.m07), Fault.DOOR_WONT_OPEN);

        // Act
        TerminalResult result = orbit.terminal.startDropOff(STATION, r.dropOffCode());

        // Assert
        assertThat(result.outcome()).isEqualTo("DOOR_DID_NOT_OPEN");
        assertThat(result.compartmentCode()).isEqualTo("M-07");
        assertThat(result.replacementAvailable()).isTrue();
        assertThat(result.sessionToken()).isNotBlank();
    }

    @Test
    @DisplayName("Door doesn't open: the new locker opens with a different code")
    void doorThatDoesNotOpenIsReplacedByALockerOfTheSameSizeOpenedWithADifferentCode() {
        // Arrange
        RegistrationResult r = reserveAndCollectInM07();
        orbit.hardware.inject(InMemoryOrbit.ref(orbit.m07), Fault.DOOR_WONT_OPEN);
        TerminalResult failed = orbit.terminal.startDropOff(STATION, r.dropOffCode());

        // Act
        TerminalResult replaced = orbit.terminal.requestReplacement(STATION, failed.sessionToken());
        String newCode = replacementCode();
        TerminalResult opened = orbit.terminal.startDropOff(STATION, newCode);

        // Assert
        assertThat(replaced.outcome()).isEqualTo("REPLACEMENT_ISSUED");
        assertThat(replaced.compartmentCode()).isEqualTo("M-11");
        assertThat(parcel(r).getCompartment().getSize()).isEqualTo(CompartmentSize.M);
        assertThat(newCode).matches("\\d{6}").isNotEqualTo(r.dropOffCode());
        assertThat(opened.outcome()).isEqualTo("DOOR_OPENED");
        assertThat(opened.compartmentCode()).isEqualTo("M-11");
    }

    @Test
    @DisplayName("Door doesn't open: the malfunction is reported to the operator")
    void doorThatDoesNotOpenIsReportedToTheOperatorAutomatically() {
        // Arrange
        RegistrationResult r = reserveAndCollectInM07();
        orbit.hardware.inject(InMemoryOrbit.ref(orbit.m07), Fault.DOOR_WONT_OPEN);

        // Act
        orbit.terminal.startDropOff(STATION, r.dropOffCode());

        // Assert
        assertThat(orbit.notifier.ofType(Notification.Type.OPERATOR_ALERT)).singleElement()
                .satisfies(n -> assertThat(n.subject()).contains(STATION + "/M-07").contains("DOOR_DID_NOT_OPEN"));
        assertThat(orbit.m07.isInService()).isFalse();
    }

    @Test
    @DisplayName("Door doesn't open: the old code stops working after the replacement")
    void oldCodeNoLongerOpensAnythingAfterTheReplacement() {
        // Arrange
        RegistrationResult r = reserveAndCollectInM07();
        orbit.hardware.inject(InMemoryOrbit.ref(orbit.m07), Fault.DOOR_WONT_OPEN);
        TerminalResult failed = orbit.terminal.startDropOff(STATION, r.dropOffCode());
        orbit.terminal.requestReplacement(STATION, failed.sessionToken());

        // Act + Assert
        assertThatThrownBy(() -> orbit.terminal.startDropOff(STATION, r.dropOffCode()))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_CODE"));
    }

    @Test
    @DisplayName("Door doesn't open: no replacement when no locker of that size is free")
    void doorThatDoesNotOpenGetsNoReplacementWhenNoOtherLockerOfTheSameSizeIsFree() {
        // Arrange: M-11 is reserved for another parcel; only the small S-01 is free
        orbit.register("M-11");
        RegistrationResult r = reserveAndCollectInM07();
        orbit.hardware.inject(InMemoryOrbit.ref(orbit.m07), Fault.DOOR_WONT_OPEN);
        TerminalResult failed = orbit.terminal.startDropOff(STATION, r.dropOffCode());

        // Act
        TerminalResult result = orbit.terminal.requestReplacement(STATION, failed.sessionToken());

        // Assert
        assertThat(failed.replacementAvailable()).isFalse();
        assertThat(result.outcome()).isEqualTo("NO_REPLACEMENT_AVAILABLE");
        assertThat(parcel(r).getCompartment()).isSameAs(orbit.m07);
        assertThat(orbit.notifier.ofType(Notification.Type.REPLACEMENT_DROP_OFF_CODE)).isEmpty();
        assertThat(orbit.notifier.ofType(Notification.Type.OPERATOR_ALERT)).hasSize(1);
    }

    // ------------------------------------------------------------------ the interface doesn't respond

    @Test
    @DisplayName("Interface doesn't respond: the courier is told to call assistance")
    void unresponsiveLockerAdvisesTheCourierToCallTheAssistanceNumber() {
        // Arrange
        RegistrationResult r = reserveAndCollectInM07();
        orbit.hardware.setStationUnreachable(true);

        // Act + Assert
        assertThatThrownBy(() -> orbit.terminal.startDropOff(STATION, r.dropOffCode()))
                .isInstanceOfSatisfying(OrbitException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(OrbitException.Kind.UNAVAILABLE);
                    assertThat(e.getCode()).isEqualTo("HARDWARE_UNAVAILABLE");
                    assertThat(e.getMessage()).contains("call assistance at " + orbit.properties.assistancePhone());
                    assertThat(e.getDetails()).containsEntry("assistancePhone", orbit.properties.assistancePhone());
                });
    }

    @Test
    @DisplayName("Interface doesn't respond: the recipient is told to call assistance")
    void unresponsiveLockerAdvisesTheRecipientToCallTheAssistanceNumber() {
        // Arrange: parcel deposited, then the locker stops responding
        RegistrationResult r = reserveAndCollectInM07();
        TerminalResult opened = orbit.terminal.startDropOff(STATION, r.dropOffCode());
        orbit.hardware.closeDoor(InMemoryOrbit.ref(orbit.m07));
        orbit.terminal.confirmDropOff(STATION, opened.sessionToken());
        String pickupCode = orbit.notifier.lastCode(Notification.Type.PICKUP_CODE).orElseThrow();
        orbit.hardware.setStationUnreachable(true);

        // Act + Assert
        assertThatThrownBy(() -> orbit.terminal.startPickup(STATION, pickupCode))
                .isInstanceOfSatisfying(OrbitException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("HARDWARE_UNAVAILABLE");
                    assertThat(e.getDetails()).containsEntry("assistancePhone", orbit.properties.assistancePhone());
                });
        assertThat(parcel(r).getState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
    }

    @Test
    @DisplayName("Interface doesn't respond: nothing changes, the code works afterwards")
    void unresponsiveLockerChangesNothingSoTheCodeWorksOnceItRespondsAgain() {
        // Arrange
        RegistrationResult r = reserveAndCollectInM07();
        orbit.hardware.setStationUnreachable(true);
        assertThatThrownBy(() -> orbit.terminal.startDropOff(STATION, r.dropOffCode()))
                .isInstanceOf(OrbitException.class);
        orbit.hardware.setStationUnreachable(false);

        // Act
        TerminalResult result = orbit.terminal.startDropOff(STATION, r.dropOffCode());

        // Assert
        assertThat(result.outcome()).isEqualTo("DOOR_OPENED");
        assertThat(result.compartmentCode()).isEqualTo("M-07");
        assertThat(orbit.m07.isInService()).isTrue();
    }

    @Test
    @DisplayName("Interface doesn't respond: every terminal answer carries the assistance number")
    void everyTerminalAnswerShowsTheAssistanceNumber() {
        // Arrange
        RegistrationResult r = reserveAndCollectInM07();

        // Act
        TerminalResult result = orbit.terminal.startDropOff(STATION, r.dropOffCode());

        // Assert
        assertThat(result.assistancePhone()).isEqualTo(orbit.properties.assistancePhone()).isNotBlank();
    }

    // ------------------------------------------------------------------ the door of the locker doesn't close

    @Test
    @DisplayName("Door doesn't close: the parcel is not marked ready; another locker is offered")
    void doorThatDoesNotCloseLetsTheUserAskForAnotherLockerAndTheParcelIsNotMarkedReady() {
        // Arrange
        RegistrationResult r = reserveAndCollectInM07();

        // Act
        TerminalResult result = dropOffWithADoorThatDoesNotClose(r);

        // Assert
        assertThat(result.outcome()).isEqualTo("DOOR_DID_NOT_CLOSE");
        assertThat(result.replacementAvailable()).isTrue();
        assertThat(parcel(r).getState()).isEqualTo(ParcelState.ON_ITS_WAY);
        assertThat(orbit.notifier.ofType(Notification.Type.PICKUP_CODE)).isEmpty();
    }

    @Test
    @DisplayName("Door doesn't close: the new locker opens with a different code")
    void doorThatDoesNotCloseIsReplacedByALockerOfTheSameSizeOpenedWithADifferentCode() {
        // Arrange
        RegistrationResult r = reserveAndCollectInM07();
        TerminalResult failed = dropOffWithADoorThatDoesNotClose(r);

        // Act
        TerminalResult replaced = orbit.terminal.requestReplacement(STATION, failed.sessionToken());
        String newCode = replacementCode();
        TerminalResult opened = orbit.terminal.startDropOff(STATION, newCode);
        orbit.hardware.closeDoor(InMemoryOrbit.ref(orbit.m11));
        TerminalResult deposited = orbit.terminal.confirmDropOff(STATION, opened.sessionToken());

        // Assert
        assertThat(replaced.compartmentCode()).isEqualTo("M-11");
        assertThat(newCode).matches("\\d{6}").isNotEqualTo(r.dropOffCode());
        assertThat(opened.compartmentCode()).isEqualTo("M-11");
        assertThat(deposited.outcome()).isEqualTo("DEPOSITED");
        assertThat(parcel(r).getState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(parcel(r).getCompartment()).isSameAs(orbit.m11);
    }

    @Test
    @DisplayName("Door doesn't close: the malfunction is reported to the operator")
    void doorThatDoesNotCloseIsReportedToTheOperatorAutomatically() {
        // Arrange
        RegistrationResult r = reserveAndCollectInM07();

        // Act
        dropOffWithADoorThatDoesNotClose(r);

        // Assert
        assertThat(orbit.notifier.ofType(Notification.Type.OPERATOR_ALERT)).singleElement()
                .satisfies(n -> assertThat(n.subject()).contains(STATION + "/M-07").contains("DOOR_DID_NOT_CLOSE"));
        assertThat(orbit.m07.isInService()).isFalse();
    }

    @Test
    @DisplayName("Door doesn't close: no replacement when no locker of that size is free")
    void doorThatDoesNotCloseGetsNoReplacementWhenNoOtherLockerOfTheSameSizeIsFree() {
        // Arrange: M-11 is reserved for another parcel; only the small S-01 is free
        orbit.register("M-11");
        RegistrationResult r = reserveAndCollectInM07();
        TerminalResult failed = dropOffWithADoorThatDoesNotClose(r);

        // Act
        TerminalResult result = orbit.terminal.requestReplacement(STATION, failed.sessionToken());

        // Assert
        assertThat(failed.replacementAvailable()).isFalse();
        assertThat(result.outcome()).isEqualTo("NO_REPLACEMENT_AVAILABLE");
        assertThat(orbit.notifier.ofType(Notification.Type.REPLACEMENT_DROP_OFF_CODE)).isEmpty();
    }
}
