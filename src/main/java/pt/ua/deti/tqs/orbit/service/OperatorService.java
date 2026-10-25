package pt.ua.deti.tqs.orbit.service;

import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.Compartment;
import pt.ua.deti.tqs.orbit.domain.CompartmentStatus;
import pt.ua.deti.tqs.orbit.domain.IncidentType;
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.domain.Parcel;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.domain.Station;
import pt.ua.deti.tqs.orbit.port.Notification;
import pt.ua.deti.tqs.orbit.port.NotifierPort;
import pt.ua.deti.tqs.orbit.repository.CompartmentRepository;
import pt.ua.deti.tqs.orbit.repository.IncidentRepository;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;
import pt.ua.deti.tqs.orbit.repository.StationRepository;
import pt.ua.deti.tqs.orbit.service.view.IncidentView;
import pt.ua.deti.tqs.orbit.service.view.ParcelDetail;
import pt.ua.deti.tqs.orbit.service.view.ParcelSummary;

/** UC6: supervise a station. */
@Service
public class OperatorService {

    private static final List<ParcelState> HOLDING_RESERVATION_NOT_DEPOSITED =
            List.of(ParcelState.CREATED, ParcelState.ON_ITS_WAY);

    private final StationRepository stations;
    private final CompartmentRepository compartments;
    private final ParcelRepository parcels;
    private final IncidentRepository incidentRepository;
    private final CompartmentAllocator allocator;
    private final PinIssuer pins;
    private final TransitionRecorder transitions;
    private final IncidentReporter incidents;
    private final NotifierPort notifier;

    public OperatorService(StationRepository stations, CompartmentRepository compartments, ParcelRepository parcels,
                           IncidentRepository incidentRepository, CompartmentAllocator allocator, PinIssuer pins,
                           TransitionRecorder transitions, IncidentReporter incidents, NotifierPort notifier) {
        this.stations = stations;
        this.compartments = compartments;
        this.parcels = parcels;
        this.incidentRepository = incidentRepository;
        this.allocator = allocator;
        this.pins = pins;
        this.transitions = transitions;
        this.incidents = incidents;
        this.notifier = notifier;
    }

    /** Parcels of a station, optionally filtered by state, newest first. */
    @Transactional(readOnly = true)
    public List<ParcelSummary> stationParcels(String stationCode, Collection<ParcelState> states) {
        Station station = station(stationCode);
        List<Parcel> list = states == null || states.isEmpty()
                ? parcels.findByStationOrderByCreatedAtDesc(station)
                : parcels.findByStationAndStateInOrderByCreatedAtDesc(station, states);
        return list.stream().map(Views::summary).toList();
    }

    @Transactional(readOnly = true)
    public List<IncidentView> incidents(String stationCode) {
        return incidentRepository.findTop50ByCompartmentStationOrderByReportedAtDesc(station(stationCode)).stream()
                .map(Views::incident).toList();
    }

    /**
     * Registers that an expired parcel was physically removed from its compartment by an operator:
     * EXPIRED -> TO_RETURN -> RETURNED (both transitions recorded), or TO_RETURN -> RETURNED.
     */
    @Transactional
    public ParcelDetail registerRemoval(String trackingCode) {
        Parcel parcel = parcel(trackingCode);
        if (parcel.getState() == ParcelState.EXPIRED) {
            transitions.apply(parcel, ParcelEvent.MARK_TO_RETURN, Actor.OPERATOR, "Operator registered removal");
        }
        transitions.apply(parcel, ParcelEvent.REMOVE, Actor.OPERATOR, "Removed from compartment by operator");
        parcel.consumePickupCode();
        notifier.send(new Notification(Notification.Type.PARCEL_EXPIRED, trackingCode, parcel.getSender().getName(),
                parcel.getSender().getEmail(), parcel.getSender().getPhone(),
                "Parcel " + trackingCode + " returned", "The parcel was not collected and is being returned."));
        return Views.detail(parcel);
    }

    /** Flags an expired parcel to be returned (EXPIRED -> TO_RETURN) without removing it yet. */
    @Transactional
    public ParcelDetail markToReturn(String trackingCode) {
        Parcel parcel = parcel(trackingCode);
        transitions.apply(parcel, ParcelEvent.MARK_TO_RETURN, Actor.OPERATOR, "Marked to return by operator");
        return Views.detail(parcel);
    }

    /**
     * Puts a compartment out of service (or back in service). A reservation not yet deposited (CREATED / ON_ITS_WAY)
     * is moved to another free compartment of the same size and a new drop-off PIN is sent to the sender; if none is
     * free the request is rejected. A deposited parcel stays where it is and can still be collected.
     */
    @Transactional
    public void setCompartmentStatus(String stationCode, String compartmentCode, CompartmentStatus status,
                                     String reason) {
        Station station = stations.findByCodeForUpdate(stationCode)
                .orElseThrow(() -> OrbitException.notFound("STATION_NOT_FOUND", "Station " + stationCode + " not found"));
        Compartment compartment = compartments.findByStationAndCode(station, compartmentCode)
                .orElseThrow(() -> OrbitException.notFound("COMPARTMENT_NOT_FOUND",
                        "Compartment " + compartmentCode + " not found"));
        if (status == CompartmentStatus.IN_SERVICE) {
            compartment.putInService();
            return;
        }
        if (!compartment.isInService()) {
            return;
        }
        List<Parcel> reserved = parcels.findByCompartmentAndStateIn(compartment, HOLDING_RESERVATION_NOT_DEPOSITED);
        String why = reason == null || reason.isBlank() ? "Put out of service by operator" : reason;
        incidents.report(compartment, null, IncidentType.MANUAL_OUT_OF_SERVICE, Actor.OPERATOR, why, true);
        for (Parcel p : reserved) {
            var free = allocator.findFree(station, p.getSize());
            if (free.isEmpty()) {
                throw OrbitException.conflict("NO_REPLACEMENT_AVAILABLE", "Compartment " + compartmentCode
                        + " holds reservation " + p.getTrackingCode() + " and no other "
                        + p.getSize().label().toLowerCase() + " compartment is free");
            }
            Compartment target = allocator.allocate(station, p.getSize(), free.get().getCode());
            PinIssuer.IssuedPin pin = pins.dropOffPin(station);
            p.relocateTo(target, pin.hash());
            notifier.send(new Notification(Notification.Type.REPLACEMENT_DROP_OFF_CODE, p.getTrackingCode(),
                    p.getSender().getName(), p.getSender().getEmail(), p.getSender().getPhone(),
                    "New compartment for " + p.getTrackingCode(), "Compartment " + compartmentCode
                    + " is out of service. The parcel was moved to " + target.getCode()
                    + ". New drop-off code: " + pin.pin() + "."));
        }
    }

    private Station station(String code) {
        return stations.findByCode(code)
                .orElseThrow(() -> OrbitException.notFound("STATION_NOT_FOUND", "Station " + code + " not found"));
    }

    private Parcel parcel(String trackingCode) {
        return parcels.findByTrackingCode(trackingCode)
                .orElseThrow(() -> OrbitException.notFound("PARCEL_NOT_FOUND", "Parcel " + trackingCode + " not found"));
    }
}
