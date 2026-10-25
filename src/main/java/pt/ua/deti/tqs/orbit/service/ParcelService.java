package pt.ua.deti.tqs.orbit.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.Compartment;
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.domain.Parcel;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.domain.Station;
import pt.ua.deti.tqs.orbit.port.Notification;
import pt.ua.deti.tqs.orbit.port.NotifierPort;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;
import pt.ua.deti.tqs.orbit.repository.StationRepository;
import pt.ua.deti.tqs.orbit.service.view.ParcelDetail;
import pt.ua.deti.tqs.orbit.service.view.ParcelSummary;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;
import pt.ua.deti.tqs.orbit.service.view.TrackingView;

/** UC2 (register), UC5 (track), and the partner / courier events outside the terminal. */
@Service
public class ParcelService {

    private static final Logger LIFECYCLE_LOG = LoggerFactory.getLogger("orbit.lifecycle");

    /** Tolerance for a lease start slightly in the past (clock skew, time spent filling the form). */
    static final Duration LEASE_START_TOLERANCE = Duration.ofMinutes(15);

    private final StationRepository stations;
    private final ParcelRepository parcels;
    private final CompartmentAllocator allocator;
    private final CodeService codes;
    private final PinIssuer pins;
    private final PricingPolicy pricing;
    private final TransitionRecorder transitions;
    private final NotifierPort notifier;
    private final Clock clock;
    private final OrbitProperties.Rules rules;

    public ParcelService(StationRepository stations, ParcelRepository parcels, CompartmentAllocator allocator,
                         CodeService codes, PinIssuer pins, PricingPolicy pricing, TransitionRecorder transitions,
                         NotifierPort notifier, Clock clock, OrbitProperties properties) {
        this.stations = stations;
        this.parcels = parcels;
        this.allocator = allocator;
        this.codes = codes;
        this.pins = pins;
        this.pricing = pricing;
        this.transitions = transitions;
        this.notifier = notifier;
        this.clock = clock;
        this.rules = properties.rules();
    }

    /**
     * Registers a parcel and reserves a compartment. The station row is locked for the duration of the transaction,
     * so concurrent registrations for the same station are serialised (and the partial unique index on
     * parcel.compartment_id is a second line of defence).
     */
    @Transactional
    public RegistrationResult register(RegisterParcelCommand cmd) {
        Instant now = clock.instant();
        pricing.validateLeaseHours(cmd.leaseHours());
        Instant leaseStart = cmd.leaseStart() == null ? now : cmd.leaseStart();
        if (leaseStart.isBefore(now.minus(LEASE_START_TOLERANCE))) {
            throw OrbitException.unprocessable("INVALID_LEASE_START", "Lease start cannot be in the past");
        }
        if (leaseStart.isAfter(now.plus(rules.maxLeaseStartAhead()))) {
            throw OrbitException.unprocessable("INVALID_LEASE_START",
                    "Lease start can be at most " + rules.maxLeaseStartAhead().toDays() + " days ahead");
        }

        Station station = stations.findByCodeForUpdate(cmd.stationCode())
                .orElseThrow(() -> OrbitException.notFound("STATION_NOT_FOUND",
                        "Station " + cmd.stationCode() + " not found"));
        Compartment compartment = allocator.allocate(station, cmd.size(), cmd.compartmentCode());

        PinIssuer.IssuedPin dropOff = pins.dropOffPin(station);
        Parcel parcel = Parcel.register(uniqueTrackingCode(now), uniqueDeliveryCode(), compartment, cmd.sender(),
                cmd.recipient(), leaseStart, cmd.leaseHours(), pricing.price(cmd.leaseHours()), dropOff.hash(), now);
        parcels.save(parcel);
        LIFECYCLE_LOG.info(
                "TRANSITION parcel={} null -> CREATED event=REGISTER actor=PARTNER station={} compartment={}",
                parcel.getTrackingCode(), station.getCode(), compartment.getCode());

        notifier.send(new Notification(Notification.Type.REGISTRATION_CONFIRMED, parcel.getTrackingCode(),
                cmd.sender().getName(), cmd.sender().getEmail(), cmd.sender().getPhone(),
                "Orbit registration " + parcel.getTrackingCode(),
                "Compartment " + compartment.getCode() + " reserved at " + station.getName() + " until "
                        + parcel.getDropOffDeadline() + ". Print the label with the QR code and drop-off PIN."));

        return new RegistrationResult(parcel.getTrackingCode(), parcel.getState(), station.getCode(),
                station.getName(), compartment.getCode(), parcel.getSize(), parcel.getLeaseStart(),
                parcel.getLeaseHours(), parcel.getDropOffDeadline(), parcel.getPrice(), rules.currency(),
                parcel.getDeliveryCode(), dropOff.pin());
    }

    @Transactional(readOnly = true)
    public TrackingView track(String trackingCode) {
        return Views.tracking(find(trackingCode));
    }

    @Transactional(readOnly = true)
    public ParcelDetail detail(String trackingCode) {
        return Views.detail(find(trackingCode));
    }

    @Transactional(readOnly = true)
    public List<ParcelSummary> recent() {
        return parcels.findTop20ByOrderByCreatedAtDesc().stream().map(Views::summary).toList();
    }

    /** Partner or recipient cancels a parcel not yet collected: the compartment is freed. */
    @Transactional
    public TrackingView cancel(String trackingCode, Actor actor, String reason) {
        Parcel parcel = find(trackingCode);
        transitions.apply(parcel, ParcelEvent.CANCEL, actor, reason == null ? "Cancelled" : reason);
        notifier.send(new Notification(Notification.Type.PARCEL_CANCELLED, trackingCode,
                parcel.getSender().getName(), parcel.getSender().getEmail(), parcel.getSender().getPhone(),
                "Parcel " + trackingCode + " cancelled", "The reservation was cancelled by the " + actor
                        .name().toLowerCase() + "."));
        return Views.tracking(parcel);
    }

    /** Courier scans the QR (delivery code) when collecting the parcel at the partner. */
    @Transactional
    public TrackingView collect(String deliveryCode, String locationLabel) {
        Parcel parcel = parcels.findByDeliveryCode(deliveryCode == null ? "" : deliveryCode.trim())
                .orElseThrow(() -> OrbitException.notFound("DELIVERY_CODE_NOT_FOUND", "Unknown delivery code"));
        if (!clock.instant().isBefore(parcel.getDropOffDeadline()) && parcel.getState() == ParcelState.CREATED) {
            throw OrbitException.conflict("DROP_OFF_DEADLINE_PASSED",
                    "The lease ended at " + parcel.getDropOffDeadline() + "; the reservation is being cancelled");
        }
        transitions.apply(parcel, ParcelEvent.COLLECT, Actor.COURIER, "Collected by courier");
        parcel.addLocation(locationLabel == null || locationLabel.isBlank() ? "Collected from sender" : locationLabel,
                null, null, clock.instant());
        return Views.tracking(parcel);
    }

    /** Courier reports where an in-transit parcel is. Only allowed while ON_ITS_WAY. */
    @Transactional
    public TrackingView addLocation(String trackingCode, String label, Double latitude, Double longitude) {
        Parcel parcel = find(trackingCode);
        if (parcel.getState() != ParcelState.ON_ITS_WAY) {
            throw new OrbitException(OrbitException.Kind.CONFLICT, "INVALID_STATE",
                    "Location updates are only accepted while the parcel is ON_ITS_WAY",
                    Map.of("currentState", parcel.getState().name()));
        }
        parcel.addLocation(label, latitude, longitude, clock.instant());
        return Views.tracking(parcel);
    }

    Parcel find(String trackingCode) {
        String code = trackingCode == null ? "" : trackingCode.trim().toUpperCase();
        return parcels.findByTrackingCode(code)
                .orElseThrow(() -> OrbitException.notFound("PARCEL_NOT_FOUND",
                        "We couldn't find this parcel. Check the characters and try again."));
    }

    private String uniqueTrackingCode(Instant now) {
        for (int i = 0; i < 50; i++) {
            String code = codes.newTrackingCode(now);
            if (!parcels.existsByTrackingCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique tracking code");
    }

    private String uniqueDeliveryCode() {
        for (int i = 0; i < 50; i++) {
            String code = codes.newDeliveryCode();
            if (!parcels.existsByDeliveryCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique delivery code");
    }
}
