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

    /**
     * Wraps a hardware adapter.
     *
     * @param delegate the adapter whose calls are logged
     */
    public LoggingLockerHardware(LockerHardwarePort delegate) {
        this.delegate = delegate;
    }

    /**
     * Delegates and logs the result (OPENED, DID_NOT_OPEN or UNREACHABLE).
     *
     * @param compartment the compartment to open
     * @return the delegate's answer
     */
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

    /**
     * Delegates and logs the door status (or UNREACHABLE).
     *
     * @param compartment the compartment to read
     * @return the delegate's answer
     */
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
