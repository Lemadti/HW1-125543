package pt.ua.deti.tqs.orbit.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.adapter.hardware.FakeLockerHardware;
import pt.ua.deti.tqs.orbit.adapter.hardware.FakeLockerHardware.Fault;
import pt.ua.deti.tqs.orbit.adapter.hardware.LoggingLockerHardware;
import pt.ua.deti.tqs.orbit.port.CompartmentRef;
import pt.ua.deti.tqs.orbit.port.DoorStatus;
import pt.ua.deti.tqs.orbit.port.HardwareUnavailableException;

/** The fake itself must be trustworthy: its state and failure injection are tested too. */
@DisplayName("FakeLockerHardware — the test double itself")
class FakeLockerHardwareTest {

    private final CompartmentRef ref = new CompartmentRef("S", "M-07");

    @Test
    @DisplayName("keeps the state of each door and records every call")
    void keepsDoorState() {
        FakeLockerHardware hw = new FakeLockerHardware(false);
        assertThat(hw.readDoor(ref)).isEqualTo(DoorStatus.CLOSED);
        assertThat(hw.openDoor(ref)).isTrue();
        assertThat(hw.readDoor(ref)).isEqualTo(DoorStatus.OPEN);
        hw.closeDoor(ref);
        assertThat(hw.readDoor(ref)).isEqualTo(DoorStatus.CLOSED);
        assertThat(hw.calls()).containsExactly("read S/M-07", "open S/M-07", "read S/M-07", "read S/M-07");
    }

    @Test
    @DisplayName("auto-close mode reads an open door as closed (the user closed it)")
    void demoModeClosesTheDoorOnRead() {
        FakeLockerHardware hw = new FakeLockerHardware(true);
        hw.openDoor(ref);
        assertThat(hw.readDoor(ref)).isEqualTo(DoorStatus.CLOSED);
    }

    @Test
    @DisplayName("injected faults make doors fail and the station unreachable")
    void injectsFailures() {
        FakeLockerHardware hw = new FakeLockerHardware(true);
        hw.inject(ref, Fault.DOOR_WONT_OPEN);
        assertThat(new LoggingLockerHardware(hw).openDoor(ref)).isFalse();

        CompartmentRef other = new CompartmentRef("S", "M-11");
        hw.inject(other, Fault.DOOR_WONT_CLOSE);
        hw.openDoor(other);
        hw.closeDoor(other);
        assertThat(hw.readDoor(other)).isEqualTo(DoorStatus.OPEN);

        hw.setStationUnreachable(true);
        assertThatThrownBy(() -> new LoggingLockerHardware(hw).readDoor(ref)).isInstanceOf(HardwareUnavailableException.class);

        hw.reset();
        assertThat(hw.activeFaults()).isEmpty();
        assertThat(hw.openDoor(ref)).isTrue();
    }
}
