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

    public enum Fault {
        DOOR_WONT_OPEN,
        DOOR_WONT_CLOSE,
        UNREACHABLE
    }

    private final Map<CompartmentRef, DoorStatus> doors = new ConcurrentHashMap<>();
    private final Map<CompartmentRef, Set<Fault>> faults = new ConcurrentHashMap<>();
    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean stationUnreachable;
    private volatile boolean autoClose;

    public FakeLockerHardware(boolean autoClose) {
        this.autoClose = autoClose;
    }

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

    /** Simulates the user physically closing the door (ignored if the door is faulty). */
    public void closeDoor(CompartmentRef compartment) {
        if (!hasFault(compartment, Fault.DOOR_WONT_CLOSE)) {
            doors.put(compartment, DoorStatus.CLOSED);
        }
    }

    public void inject(CompartmentRef compartment, Fault fault) {
        faults.computeIfAbsent(compartment, k -> ConcurrentHashMap.newKeySet()).add(fault);
    }

    public void setStationUnreachable(boolean unreachable) {
        this.stationUnreachable = unreachable;
    }

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

    public DoorStatus doorStatus(CompartmentRef compartment) {
        return doors.getOrDefault(compartment, DoorStatus.CLOSED);
    }

    public Map<CompartmentRef, Set<Fault>> activeFaults() {
        return Map.copyOf(faults);
    }

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
