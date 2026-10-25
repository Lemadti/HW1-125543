package pt.ua.deti.tqs.orbit.adapter.hardware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import pt.ua.deti.tqs.orbit.port.CompartmentRef;
import pt.ua.deti.tqs.orbit.port.DoorStatus;
import pt.ua.deti.tqs.orbit.port.HardwareUnavailableException;
import pt.ua.deti.tqs.orbit.port.LockerHardwarePort;

/**
 * In-memory fake of the locker hardware. Keeps the door state of every compartment and can be configured to inject
 * the failures that matter for the business rules:
 * <ul>
 *   <li>{@link Fault#DOOR_WONT_OPEN}: the lock does not release ({@code openDoor} returns false);</li>
 *   <li>{@link Fault#DOOR_WONT_CLOSE}: the door stays open (the sensor keeps reporting OPEN);</li>
 *   <li>{@link Fault#UNREACHABLE}: the controller does not answer (throws {@link HardwareUnavailableException}).</li>
 * </ul>
 * With {@code autoClose = true} (demo configuration) an opened door reports CLOSED on the next read, i.e. it behaves
 * as a working locker where the user closes the door.
 */
public class FakeLockerHardware implements LockerHardwarePort {

    /** Failures the fake can inject. */
    public enum Fault {
        /** The lock does not release: {@code openDoor} returns false. */
        DOOR_WONT_OPEN,
        /** The door stays open: the sensor keeps reporting OPEN. */
        DOOR_WONT_CLOSE,
        /** The controller does not answer: calls throw {@link HardwareUnavailableException}. */
        UNREACHABLE
    }

    private final Map<CompartmentRef, DoorStatus> doors = new ConcurrentHashMap<>();
    private final Map<CompartmentRef, Set<Fault>> faults = new ConcurrentHashMap<>();
    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean stationUnreachable;
    private volatile boolean autoClose;

    /**
     * Creates the fake with every door closed and no faults.
     *
     * @param autoClose true to behave as a working locker: an opened door reads as closed on the next read
     */
    public FakeLockerHardware(boolean autoClose) {
        this.autoClose = autoClose;
    }

    /**
     * Opens the door unless a {@link Fault#DOOR_WONT_OPEN} is injected.
     *
     * @param compartment the compartment to open
     * @return true if the door is now open, false if the lock did not release
     * @throws HardwareUnavailableException if the station or compartment is set as unreachable
     */
    @Override
    public boolean openDoor(CompartmentRef compartment) {
        calls.add("open " + compartment);
        failIfUnreachable(compartment);
        if (hasFault(compartment, Fault.DOOR_WONT_OPEN)) {
            return false;
        }
        doors.put(compartment, DoorStatus.OPEN);
        return true;
    }

    /**
     * Reads the simulated door sensor. In auto-close mode an open door (without
     * {@link Fault#DOOR_WONT_CLOSE}) is closed by this read, simulating the user closing it.
     *
     * @param compartment the compartment to read
     * @return the door status
     * @throws HardwareUnavailableException if the station or compartment is set as unreachable
     */
    @Override
    public DoorStatus readDoor(CompartmentRef compartment) {
        calls.add("read " + compartment);
        failIfUnreachable(compartment);
        DoorStatus status = doors.getOrDefault(compartment, DoorStatus.CLOSED);
        if (status == DoorStatus.OPEN && autoClose && !hasFault(compartment, Fault.DOOR_WONT_CLOSE)) {
            doors.put(compartment, DoorStatus.CLOSED);
            return DoorStatus.CLOSED;
        }
        return status;
    }

    /**
     * Simulates the user physically closing the door (ignored if the door is faulty).
     *
     * @param compartment the compartment whose door is closed
     */
    public void closeDoor(CompartmentRef compartment) {
        if (!hasFault(compartment, Fault.DOOR_WONT_CLOSE)) {
            doors.put(compartment, DoorStatus.CLOSED);
        }
    }

    /**
     * Injects a failure for one compartment. Failures accumulate until {@link #reset()}.
     *
     * @param compartment the compartment that will misbehave
     * @param fault       the failure to inject
     */
    public void inject(CompartmentRef compartment, Fault fault) {
        faults.computeIfAbsent(compartment, k -> ConcurrentHashMap.newKeySet()).add(fault);
    }

    /**
     * Makes every call fail as if the station controller did not answer.
     *
     * @param unreachable true to simulate a controller that does not respond
     */
    public void setStationUnreachable(boolean unreachable) {
        this.stationUnreachable = unreachable;
    }

    /**
     * Switches the auto-close (working locker) behaviour.
     *
     * @param autoClose true to close opened doors on the next read
     */
    public void setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
    }

    /** Removes all faults and resets every door to closed. */
    public void reset() {
        faults.clear();
        doors.clear();
        calls.clear();
        stationUnreachable = false;
    }

    /**
     * Current simulated door state, without logging a call.
     *
     * @param compartment the compartment to inspect
     * @return OPEN or CLOSED
     */
    public DoorStatus doorStatus(CompartmentRef compartment) {
        return doors.getOrDefault(compartment, DoorStatus.CLOSED);
    }

    /**
     * Faults currently injected.
     *
     * @return an immutable copy of the faults per compartment
     */
    public Map<CompartmentRef, Set<Fault>> activeFaults() {
        return Map.copyOf(faults);
    }

    /**
     * Every call made to the fake, in order (e.g. "open ORB-AVR-001/M-07").
     *
     * @return an immutable copy of the call log
     */
    public List<String> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    private boolean hasFault(CompartmentRef compartment, Fault fault) {
        return faults.getOrDefault(compartment, Set.of()).contains(fault);
    }

    private void failIfUnreachable(CompartmentRef compartment) {
        if (stationUnreachable || hasFault(compartment, Fault.UNREACHABLE)) {
            throw new HardwareUnavailableException("Station controller not responding for " + compartment);
        }
    }
}
