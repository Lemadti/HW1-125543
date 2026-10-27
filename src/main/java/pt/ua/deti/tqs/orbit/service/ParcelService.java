package pt.ua.deti.tqs.orbit.service;

import java.time.Clock;
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
import pt.ua.deti.tqs.orbit.domain.DropOffWindow;
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

    /**
     * Creates the service.
     *
     * @param stations    station repository (with the allocation lock)
     * @param parcels     parcel repository
     * @param allocator   compartment allocation rules
     * @param codes       tracking and delivery codes
     * @param pins        drop-off PINs
     * @param pricing     reservation price
     * @param transitions records transitions
     * @param notifier    informs the sender
     * @param clock       time source
     * @param properties  business rules
     */
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
     * Registers a parcel and reserves a compartment (UC2). The drop-off deadline is {@code drop-off-working-days}
     * working days after now (the allocation) and the price is the same for every parcel. The station row is locked
     * for the duration of the
     * transaction, so concurrent registrations for the same station are serialised (and the partial unique index on
     * {@code parcel.compartment_id} is a second line of defence).
     *
     * @param cmd station, size, optional compartment and contacts
     * @return tracking code, delivery code, drop-off PIN and reservation details
     * @throws OrbitException STATION_NOT_FOUND, STATION_FULL, COMPARTMENT_NOT_AVAILABLE, COMPARTMENT_NOT_FOUND or
     *         SIZE_MISMATCH
     */
    @Transactional
    public RegistrationResult register(RegisterParcelCommand cmd) {
        Instant now = clock.instant();
        Station station = stations.findByCodeForUpdate(cmd.stationCode())
                .orElseThrow(() -> OrbitException.notFound("STATION_NOT_FOUND",
                        "Station " + cmd.stationCode() + " not found"));
        Compartment compartment = allocator.allocate(station, cmd.size(), cmd.compartmentCode());

        PinIssuer.IssuedPin dropOff = pins.dropOffPin(station);
        Instant dropOffDeadline = DropOffWindow.deadline(now, rules.dropOffWorkingDays(), rules.zone());
        Parcel parcel = Parcel.register(uniqueTrackingCode(now), uniqueDeliveryCode(), compartment, cmd.sender(),
                cmd.recipient(), dropOffDeadline, pricing.price(), dropOff.hash(), now);
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
                station.getName(), compartment.getCode(), parcel.getSize(), parcel.getDropOffDeadline(),
                parcel.getPrice(), rules.currency(),
                parcel.getDeliveryCode(), dropOff.pin());
    }

    /**
     * Public tracking information (UC5).
     *
     * @param trackingCode tracking code (case-insensitive)
     * @return state, history and locations, with the recipient name masked
     * @throws OrbitException PARCEL_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public TrackingView track(String trackingCode) {
        return Views.tracking(find(trackingCode));
    }

    /**
     * Full parcel information for the operator.
     *
     * @param trackingCode tracking code
     * @return the parcel detail
     * @throws OrbitException PARCEL_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public ParcelDetail detail(String trackingCode) {
        return Views.detail(find(trackingCode));
    }

    /**
     * The 20 most recently registered parcels.
     *
     * @return the parcels, newest first
     */
    @Transactional(readOnly = true)
    public List<ParcelSummary> recent() {
        return parcels.findTop20ByOrderByCreatedAtDesc().stream().map(Views::summary).toList();
    }

    /**
     * Cancels a parcel not yet collected (CREATED to CANCELLED); the compartment is freed.
     *
     * @param trackingCode tracking code
     * @param actor        PARTNER or RECIPIENT
     * @param reason       optional reason
     * @return the updated tracking view
     * @throws pt.ua.deti.tqs.orbit.domain.InvalidTransitionException if the parcel is not CREATED
     */
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

    /**
     * The courier scans the delivery code at the partner (CREATED to ON_ITS_WAY).
     *
     * @param deliveryCode  QR content from the label
     * @param locationLabel optional location of the collection
     * @return the updated tracking view
     * @throws OrbitException DELIVERY_CODE_NOT_FOUND or DROP_OFF_DEADLINE_PASSED
     */
    @Transactional
    public TrackingView collect(String deliveryCode, String locationLabel) {
        Parcel parcel = parcels.findByDeliveryCode(deliveryCode == null ? "" : deliveryCode.trim())
                .orElseThrow(() -> OrbitException.notFound("DELIVERY_CODE_NOT_FOUND", "Unknown delivery code"));
        if (!DropOffWindow.isOpen(parcel.getDropOffDeadline(), clock.instant())
                && parcel.getState() == ParcelState.CREATED) {
            throw OrbitException.conflict("DROP_OFF_DEADLINE_PASSED", "The drop-off window ended at "
                    + parcel.getDropOffDeadline() + "; the reservation is being cancelled");
        }
        transitions.apply(parcel, ParcelEvent.COLLECT, Actor.COURIER, "Collected by courier");
        parcel.addLocation(locationLabel == null || locationLabel.isBlank() ? "Collected from sender" : locationLabel,
                null, null, clock.instant());
        return Views.tracking(parcel);
    }

    /**
     * Records where an in-transit parcel is.
     *
     * @param trackingCode tracking code
     * @param label        location description
     * @param latitude     optional latitude
     * @param longitude    optional longitude
     * @return the updated tracking view
     * @throws OrbitException INVALID_STATE if the parcel is not ON_ITS_WAY
     */
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

    /**
     * Loads a parcel by tracking code (trimmed, upper case).
     *
     * @param trackingCode tracking code
     * @return the parcel
     * @throws OrbitException PARCEL_NOT_FOUND
     */
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
