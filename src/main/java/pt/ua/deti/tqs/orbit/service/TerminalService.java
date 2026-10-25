package pt.ua.deti.tqs.orbit.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.Compartment;
import pt.ua.deti.tqs.orbit.domain.IncidentType;
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.domain.Parcel;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.domain.ParcelStateMachine;
import pt.ua.deti.tqs.orbit.domain.InvalidTransitionException;
import pt.ua.deti.tqs.orbit.domain.Station;
import pt.ua.deti.tqs.orbit.port.CompartmentRef;
import pt.ua.deti.tqs.orbit.port.DoorStatus;
import pt.ua.deti.tqs.orbit.port.HardwareUnavailableException;
import pt.ua.deti.tqs.orbit.port.LockerHardwarePort;
import pt.ua.deti.tqs.orbit.port.Notification;
import pt.ua.deti.tqs.orbit.port.NotifierPort;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;
import pt.ua.deti.tqs.orbit.repository.StationRepository;
import pt.ua.deti.tqs.orbit.service.view.TerminalResult;

/**
 * Locker terminal (UC3 drop-off, UC4 pickup), including the hardware failure rules.
 *
 * <p>Interaction is synchronous and in two steps: (1) a code is entered, the backend asks the hardware port to open
 * the door and returns a session token; (2) the user says the door is closed, the backend reads the door sensor and
 * completes the transition. Door failures put the compartment out of service and alert the operator; during
 * drop-off the courier can ask for a replacement compartment of the same size.</p>
 */
@Service
public class TerminalService {

    static final String DOOR_OPENED = "DOOR_OPENED";
    static final String DEPOSITED = "DEPOSITED";
    static final String COMPLETED = "COMPLETED";
    static final String DOOR_DID_NOT_OPEN = "DOOR_DID_NOT_OPEN";
    static final String DOOR_DID_NOT_CLOSE = "DOOR_DID_NOT_CLOSE";
    static final String REPLACEMENT_ISSUED = "REPLACEMENT_ISSUED";
    static final String NO_REPLACEMENT_AVAILABLE = "NO_REPLACEMENT_AVAILABLE";

    private static final List<ParcelState> DROP_OFF_STATES = List.of(ParcelState.CREATED, ParcelState.ON_ITS_WAY);

    private final StationRepository stations;
    private final ParcelRepository parcels;
    private final LockerHardwarePort hardware;
    private final NotifierPort notifier;
    private final CodeService codes;
    private final PinIssuer pins;
    private final AttemptGuard guard;
    private final CompartmentAllocator allocator;
    private final TransitionRecorder transitions;
    private final IncidentReporter incidents;
    private final Clock clock;
    private final OrbitProperties properties;

    public TerminalService(StationRepository stations, ParcelRepository parcels, LockerHardwarePort hardware,
                           NotifierPort notifier, CodeService codes, PinIssuer pins, AttemptGuard guard,
                           CompartmentAllocator allocator, TransitionRecorder transitions,
                           IncidentReporter incidents, Clock clock, OrbitProperties properties) {
        this.stations = stations;
        this.parcels = parcels;
        this.hardware = hardware;
        this.notifier = notifier;
        this.codes = codes;
        this.pins = pins;
        this.guard = guard;
        this.allocator = allocator;
        this.transitions = transitions;
        this.incidents = incidents;
        this.clock = clock;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ drop-off (courier)

    /** Step 1 of UC3: the courier enters the drop-off PIN printed on the label. */
    @Transactional(noRollbackFor = OrbitException.class)
    public TerminalResult startDropOff(String stationCode, String pin) {
        Station station = station(stationCode);
        String key = stationCode + ":DROP_OFF";
        guard.ensureNotLocked(key);
        Parcel parcel = (codes.isWellFormedPin(pin)
                ? parcels.findByStationAndDropOffCodeHashAndStateIn(station, codes.hash(pin), DROP_OFF_STATES)
                : java.util.Optional.<Parcel>empty())
                .orElseThrow(() -> invalidCode(guard.recordFailure(key)));
        guard.recordSuccess(key);

        if (ParcelStateMachine.target(parcel.getState(), ParcelEvent.DEPOSIT).isEmpty()) {
            throw new InvalidTransitionException(parcel.getState(), ParcelEvent.DEPOSIT, Actor.COURIER,
                    "The parcel must be collected (QR code scanned) before it can be dropped off");
        }
        return openForDropOff(parcel);
    }

    /** Step 2 of UC3: the courier closed the door. */
    @Transactional
    public TerminalResult confirmDropOff(String stationCode, String token) {
        Parcel parcel = session(stationCode, token);
        Compartment compartment = parcel.getCompartment();
        DoorStatus door = readDoor(parcel, compartment);
        if (door != DoorStatus.CLOSED) {
            incidents.report(compartment, parcel.getTrackingCode(), IncidentType.DOOR_DID_NOT_CLOSE, Actor.COURIER,
                    "Door did not close after drop-off", true);
            boolean replacement = allocator.findFree(parcel.getStation(), parcel.getSize()).isPresent();
            return result(DOOR_DID_NOT_CLOSE, "The door of " + compartment.getCode() + " did not close. "
                    + (replacement ? "Take the parcel out and ask for another compartment."
                    : "No other compartment is free; keep the parcel and call assistance."), parcel, token, replacement);
        }

        // Expired parcels flagged TO_RETURN in this compartment were taken out by the courier.
        for (Parcel old : parcels.findByCompartmentAndStateIn(compartment, List.of(ParcelState.TO_RETURN))) {
            transitions.apply(old, ParcelEvent.REMOVE, Actor.COURIER,
                    "Removed by courier during drop-off of " + parcel.getTrackingCode());
        }

        Instant now = clock.instant();
        PinIssuer.IssuedPin pickup = pins.pickupPin(parcel.getStation(), parcel.getDropOffCodeHash());
        transitions.apply(parcel, ParcelEvent.DEPOSIT, Actor.COURIER,
                "Deposited in " + compartment.getCode() + ", door closed");
        parcel.markDeposited(now, now.plus(properties.rules().pickupWindow()), pickup.hash());
        parcel.clearTerminalSession();

        notifier.send(new Notification(Notification.Type.PICKUP_CODE, parcel.getTrackingCode(),
                parcel.getRecipient().getName(), parcel.getRecipient().getEmail(), parcel.getRecipient().getPhone(),
                "Your parcel is ready at " + parcel.getStation().getName(),
                "Parcel " + parcel.getTrackingCode() + " is ready for pickup at " + parcel.getStation().getName()
                        + " (" + parcel.getStation().getAddress() + "), compartment " + compartment.getCode()
                        + ". Pickup code: " + pickup.pin() + ". Valid until " + parcel.getPickupDeadline()
                        + ". Single use."));
        return result(DEPOSITED, "Thank you! The parcel is stored and the recipient was notified.", parcel, null,
                null);
    }

    /**
     * After a door failure during drop-off, the courier asks for another compartment of the same size. A new drop-off
     * PIN for the new compartment is sent to the sender's contact (as it would be printed on a new label).
     */
    @Transactional
    public TerminalResult requestReplacement(String stationCode, String token) {
        Parcel parcel = session(stationCode, token);
        Compartment failed = parcel.getCompartment();
        if (failed.isInService() || parcel.getState() != ParcelState.ON_ITS_WAY) {
            throw OrbitException.conflict("NO_FAILURE_REPORTED",
                    "A replacement can only be requested after a door failure during drop-off");
        }
        Station station = stations.findByCodeForUpdate(stationCode).orElseThrow();
        parcel.clearTerminalSession();
        var free = allocator.findFree(station, parcel.getSize());
        if (free.isEmpty()) {
            return result(NO_REPLACEMENT_AVAILABLE, "No other " + parcel.getSize().label().toLowerCase()
                    + " compartment is free. Keep the parcel; the operator was notified.", parcel, null, false);
        }
        Compartment replacement = allocator.allocate(station, parcel.getSize(), free.get().getCode());
        PinIssuer.IssuedPin pin = pins.dropOffPin(station);
        parcel.relocateTo(replacement, pin.hash());
        notifier.send(new Notification(Notification.Type.REPLACEMENT_DROP_OFF_CODE, parcel.getTrackingCode(),
                parcel.getSender().getName(), parcel.getSender().getEmail(), parcel.getSender().getPhone(),
                "New drop-off code for " + parcel.getTrackingCode(),
                "Compartment " + failed.getCode() + " at " + station.getName() + " failed. The parcel was moved to "
                        + replacement.getCode() + ". New drop-off code: " + pin.pin() + "."));
        return result(REPLACEMENT_ISSUED, "Compartment " + replacement.getCode()
                + " was assigned. A new drop-off code was sent; enter it to open the new compartment.", parcel,
                null, null);
    }

    // ------------------------------------------------------------------ pickup (recipient)

    /** Step 1 of UC4: the recipient enters the pickup PIN. On door opened the parcel is PICKED_UP. */
    @Transactional(noRollbackFor = OrbitException.class)
    public TerminalResult startPickup(String stationCode, String pin) {
        Station station = station(stationCode);
        String key = stationCode + ":PICK_UP";
        guard.ensureNotLocked(key);
        Parcel parcel = (codes.isWellFormedPin(pin)
                ? parcels.findByStationAndPickupCodeHashAndStateIn(station, codes.hash(pin), ParcelState.COLLECTABLE)
                : java.util.Optional.<Parcel>empty())
                .orElseThrow(() -> invalidCode(guard.recordFailure(key)));
        guard.recordSuccess(key);

        Compartment compartment = parcel.getCompartment();
        boolean opened = openDoor(parcel, compartment);
        if (!opened) {
            incidents.report(compartment, parcel.getTrackingCode(), IncidentType.DOOR_DID_NOT_OPEN, Actor.RECIPIENT,
                    "Door did not open for pickup; parcel still inside", true);
            return result(DOOR_DID_NOT_OPEN, "The compartment did not open. Your code is still valid. "
                    + "Please call assistance at " + properties.assistancePhone() + ".", parcel, null, null);
        }
        transitions.apply(parcel, ParcelEvent.PICK_UP, Actor.RECIPIENT, "Picked up from " + compartment.getCode());
        parcel.consumePickupCode();
        String token = newSession(parcel);
        notifier.send(new Notification(Notification.Type.PARCEL_PICKED_UP, parcel.getTrackingCode(),
                parcel.getSender().getName(), parcel.getSender().getEmail(), parcel.getSender().getPhone(),
                "Parcel " + parcel.getTrackingCode() + " delivered", "The recipient collected the parcel."));
        return result(DOOR_OPENED, "Compartment " + compartment.getCode()
                + " is open. Take your parcel and close the door.", parcel, token, null);
    }

    /** Step 2 of UC4: the recipient closed the door; the compartment is free again. */
    @Transactional
    public TerminalResult confirmPickup(String stationCode, String token) {
        Parcel parcel = session(stationCode, token);
        Compartment compartment = parcel.getCompartment();
        DoorStatus door = readDoor(parcel, compartment);
        parcel.clearTerminalSession();
        if (door != DoorStatus.CLOSED) {
            incidents.report(compartment, parcel.getTrackingCode(), IncidentType.DOOR_DID_NOT_CLOSE,
                    Actor.RECIPIENT, "Door did not close after pickup", true);
            return result(DOOR_DID_NOT_CLOSE, "The door did not close. The operator was notified - thank you!",
                    parcel, null, null);
        }
        return result(COMPLETED, "Enjoy your parcel!", parcel, null, null);
    }

    // ------------------------------------------------------------------ helpers

    private TerminalResult openForDropOff(Parcel parcel) {
        Compartment compartment = parcel.getCompartment();
        if (!compartment.isInService()) {
            boolean replacement = allocator.findFree(parcel.getStation(), parcel.getSize()).isPresent();
            String token = newSession(parcel);
            return result(DOOR_DID_NOT_OPEN, "Compartment " + compartment.getCode() + " is out of service. "
                    + (replacement ? "Ask for another compartment." : "No other compartment is free."), parcel,
                    token, replacement);
        }
        boolean opened = openDoor(parcel, compartment);
        String token = newSession(parcel);
        if (!opened) {
            incidents.report(compartment, parcel.getTrackingCode(), IncidentType.DOOR_DID_NOT_OPEN, Actor.COURIER,
                    "Door did not open for drop-off", true);
            boolean replacement = allocator.findFree(parcel.getStation(), parcel.getSize()).isPresent();
            return result(DOOR_DID_NOT_OPEN, "Compartment " + compartment.getCode() + " did not open. "
                    + (replacement ? "You can ask for another compartment." : "No other compartment is free."),
                    parcel, token, replacement);
        }
        return result(DOOR_OPENED, "Compartment " + compartment.getCode()
                + " is open. Place the parcel inside and close the door.", parcel, token, null);
    }

    private boolean openDoor(Parcel parcel, Compartment compartment) {
        try {
            return hardware.openDoor(ref(compartment));
        } catch (HardwareUnavailableException e) {
            throw hardwareUnavailable(parcel, compartment, e);
        }
    }

    private DoorStatus readDoor(Parcel parcel, Compartment compartment) {
        try {
            return hardware.readDoor(ref(compartment));
        } catch (HardwareUnavailableException e) {
            throw hardwareUnavailable(parcel, compartment, e);
        }
    }

    private OrbitException hardwareUnavailable(Parcel parcel, Compartment compartment, HardwareUnavailableException e) {
        incidents.report(compartment, parcel.getTrackingCode(), IncidentType.HARDWARE_UNREACHABLE, Actor.SYSTEM,
                e.getMessage(), false);
        return new OrbitException(OrbitException.Kind.UNAVAILABLE, "HARDWARE_UNAVAILABLE",
                "The locker is not responding. Please call assistance at " + properties.assistancePhone() + ".",
                Map.of("assistancePhone", properties.assistancePhone()));
    }

    private String newSession(Parcel parcel) {
        String token = UUID.randomUUID().toString();
        parcel.openTerminalSession(token, clock.instant().plus(properties.rules().terminalSessionTimeout()));
        return token;
    }

    private Parcel session(String stationCode, String token) {
        Station station = station(stationCode);
        return parcels.findByStationAndTerminalSession(station, token)
                .filter(p -> p.hasTerminalSession(token, clock.instant()))
                .orElseThrow(() -> OrbitException.notFound("SESSION_NOT_FOUND",
                        "This terminal session expired. Please start again."));
    }

    private Station station(String code) {
        return stations.findByCode(code)
                .orElseThrow(() -> OrbitException.notFound("STATION_NOT_FOUND", "Station " + code + " not found"));
    }

    private static OrbitException invalidCode(int remaining) {
        return new OrbitException(OrbitException.Kind.UNPROCESSABLE, "INVALID_CODE",
                "This code is not valid at this station. " + remaining + " attempt(s) left.",
                Map.of("remainingAttempts", remaining));
    }

    private TerminalResult result(String outcome, String message, Parcel parcel, String token, Boolean replacement) {
        return new TerminalResult(outcome, message, parcel.getTrackingCode(), parcel.getState(),
                parcel.getCompartment().getCode(), token, replacement, properties.assistancePhone());
    }

    private static CompartmentRef ref(Compartment c) {
        return new CompartmentRef(c.getStation().getCode(), c.getCode());
    }
}
