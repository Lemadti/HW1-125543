package pt.ua.deti.tqs.orbit.adapter.hardware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ua.deti.tqs.orbit.port.CompartmentRef;
import pt.ua.deti.tqs.orbit.port.DoorStatus;
import pt.ua.deti.tqs.orbit.port.HardwareUnavailableException;
import pt.ua.deti.tqs.orbit.port.LockerHardwarePort;

/** Decorator that logs every call to the hardware port, whatever the adapter behind it. */
public class LoggingLockerHardware implements LockerHardwarePort {

    private static final Logger log = LoggerFactory.getLogger("orbit.hardware");

    private final LockerHardwarePort delegate;

    public LoggingLockerHardware(LockerHardwarePort delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean openDoor(CompartmentRef compartment) {
        try {
            boolean opened = delegate.openDoor(compartment);
            log.info("HW openDoor {} -> {}", compartment, opened ? "OPENED" : "DID_NOT_OPEN");
            return opened;
        } catch (HardwareUnavailableException e) {
            log.warn("HW openDoor {} -> UNREACHABLE ({})", compartment, e.getMessage());
            throw e;
        }
    }

    @Override
    public DoorStatus readDoor(CompartmentRef compartment) {
        try {
            DoorStatus status = delegate.readDoor(compartment);
            log.info("HW readDoor {} -> {}", compartment, status);
            return status;
        } catch (HardwareUnavailableException e) {
            log.warn("HW readDoor {} -> UNREACHABLE ({})", compartment, e.getMessage());
            throw e;
        }
    }
}
