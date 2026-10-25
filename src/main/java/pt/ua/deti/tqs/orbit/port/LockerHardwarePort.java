package pt.ua.deti.tqs.orbit.port;

/**
 * Port to the locker hardware, owned by the domain. Synchronous request/response: the business logic asks the
 * station to unlock a door and later reads the door sensor when the user says it is closed.
 *
 * <p>Implementations throw {@link HardwareUnavailableException} when the station controller does not answer.</p>
 */
public interface LockerHardwarePort {

    /**
     * Unlocks the compartment door.
     *
     * @param compartment the compartment to open
     * @return true if the controller confirms the door is open; false if the lock did not release
     * @throws HardwareUnavailableException if the station controller does not answer
     */
    boolean openDoor(CompartmentRef compartment);

    /**
     * Reads the door sensor.
     *
     * @param compartment the compartment to read
     * @return OPEN or CLOSED
     * @throws HardwareUnavailableException if the station controller does not answer
     */
    DoorStatus readDoor(CompartmentRef compartment);
}
