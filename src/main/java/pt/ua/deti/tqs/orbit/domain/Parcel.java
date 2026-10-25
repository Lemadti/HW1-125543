package pt.ua.deti.tqs.orbit.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A parcel in transit. The lifecycle rules live in {@link ParcelStateMachine}; this entity only allows its state to
 * change through {@link #apply}, which validates the event and records the transition.
 */
@Entity
@Table(name = "parcel")
public class Parcel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    /** Public tracking code, e.g. ORB-261024-0714. */
    @Column(name = "tracking_code", nullable = false, unique = true)
    private String trackingCode;

    /** Content of the QR code printed on the box, scanned by the courier at the partner. */
    @Column(name = "delivery_code", nullable = false, unique = true)
    private String deliveryCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id")
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compartment_id")
    private Compartment compartment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompartmentSize size;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParcelState state;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "sender_name", nullable = false)),
            @AttributeOverride(name = "email", column = @Column(name = "sender_email", nullable = false)),
            @AttributeOverride(name = "phone", column = @Column(name = "sender_phone", nullable = false))
    })
    private Contact sender;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "recipient_name", nullable = false)),
            @AttributeOverride(name = "email", column = @Column(name = "recipient_email", nullable = false)),
            @AttributeOverride(name = "phone", column = @Column(name = "recipient_phone", nullable = false))
    })
    private Contact recipient;

    @Column(name = "lease_start", nullable = false)
    private Instant leaseStart;

    @Column(name = "lease_hours", nullable = false)
    private int leaseHours;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** End of the lease: the courier must collect and drop off the parcel until then. */
    @Column(name = "drop_off_deadline", nullable = false)
    private Instant dropOffDeadline;

    @Column(name = "deposited_at")
    private Instant depositedAt;

    @Column(name = "pickup_deadline")
    private Instant pickupDeadline;

    /** HMAC of the 6-digit drop-off PIN (never stored in clear). Cleared once used. */
    @Column(name = "drop_off_code_hash")
    private String dropOffCodeHash;

    /** HMAC of the 6-digit pickup PIN (never stored in clear). Cleared once used (single use). */
    @Column(name = "pickup_code_hash")
    private String pickupCodeHash;

    /** Token of an ongoing terminal interaction (door opened, waiting for the door to be closed). */
    @Column(name = "terminal_session")
    private String terminalSession;

    @Column(name = "terminal_session_expires_at")
    private Instant terminalSessionExpiresAt;

    @OneToMany(mappedBy = "parcel", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt ASC, id ASC")
    private List<ParcelTransition> history = new ArrayList<>();

    @OneToMany(mappedBy = "parcel", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("scannedAt ASC, id ASC")
    private List<LocationScan> locations = new ArrayList<>();

    protected Parcel() {
    }

    /** Registers a parcel: initial state CREATED, compartment reserved, transition recorded. */
    public static Parcel register(String trackingCode, String deliveryCode, Compartment compartment,
                                  Contact sender, Contact recipient, Instant leaseStart, int leaseHours,
                                  BigDecimal price, String dropOffCodeHash, Instant now) {
        Parcel p = new Parcel();
        p.trackingCode = trackingCode;
        p.deliveryCode = deliveryCode;
        p.station = compartment.getStation();
        p.compartment = compartment;
        p.size = compartment.getSize();
        p.sender = sender;
        p.recipient = recipient;
        p.leaseStart = leaseStart;
        p.leaseHours = leaseHours;
        p.price = price;
        p.createdAt = now;
        p.dropOffDeadline = leaseStart.plusSeconds(leaseHours * 3600L);
        p.dropOffCodeHash = dropOffCodeHash;
        p.state = ParcelState.CREATED;
        p.history.add(new ParcelTransition(p, null, ParcelState.CREATED, null, Actor.PARTNER, now,
                "Registered, compartment " + compartment.getCode() + " reserved"));
        return p;
    }

    /**
     * Applies a lifecycle event.
     *
     * @return the recorded transition
     * @throws InvalidTransitionException if the event is not valid in the current state or for this actor
     */
    public ParcelTransition apply(ParcelEvent event, Actor actor, Instant at, String note) {
        ParcelState to = ParcelStateMachine.next(state, event, actor);
        ParcelTransition t = new ParcelTransition(this, state, to, event, actor, at, note);
        history.add(t);
        state = to;
        return t;
    }

    public void markDeposited(Instant at, Instant pickupDeadline, String pickupCodeHash) {
        this.depositedAt = at;
        this.pickupDeadline = pickupDeadline;
        this.pickupCodeHash = pickupCodeHash;
        this.dropOffCodeHash = null;
    }

    /** Moves the reservation to another compartment of the same size (hardware failure / out of service). */
    public void relocateTo(Compartment other, String newDropOffCodeHash) {
        if (other.getSize() != size) {
            throw new IllegalArgumentException("Compartment size mismatch");
        }
        this.compartment = other;
        this.dropOffCodeHash = newDropOffCodeHash;
    }

    public void consumePickupCode() {
        this.pickupCodeHash = null;
    }

    public void openTerminalSession(String token, Instant expiresAt) {
        this.terminalSession = token;
        this.terminalSessionExpiresAt = expiresAt;
    }

    public boolean hasTerminalSession(String token, Instant now) {
        return terminalSession != null && terminalSession.equals(token)
                && terminalSessionExpiresAt != null && now.isBefore(terminalSessionExpiresAt);
    }

    public void clearTerminalSession() {
        this.terminalSession = null;
        this.terminalSessionExpiresAt = null;
    }

    public LocationScan addLocation(String label, Double latitude, Double longitude, Instant at) {
        LocationScan scan = new LocationScan(this, label, latitude, longitude, at);
        locations.add(scan);
        return scan;
    }

    public Optional<LocationScan> lastLocation() {
        return locations.isEmpty() ? Optional.empty() : Optional.of(locations.get(locations.size() - 1));
    }

    public Long getId() {
        return id;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public String getDeliveryCode() {
        return deliveryCode;
    }

    public Station getStation() {
        return station;
    }

    public Compartment getCompartment() {
        return compartment;
    }

    public CompartmentSize getSize() {
        return size;
    }

    public ParcelState getState() {
        return state;
    }

    public Contact getSender() {
        return sender;
    }

    public Contact getRecipient() {
        return recipient;
    }

    public Instant getLeaseStart() {
        return leaseStart;
    }

    public int getLeaseHours() {
        return leaseHours;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDropOffDeadline() {
        return dropOffDeadline;
    }

    public Instant getDepositedAt() {
        return depositedAt;
    }

    public Instant getPickupDeadline() {
        return pickupDeadline;
    }

    public String getDropOffCodeHash() {
        return dropOffCodeHash;
    }

    public String getPickupCodeHash() {
        return pickupCodeHash;
    }

    public List<ParcelTransition> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public List<LocationScan> getLocations() {
        return Collections.unmodifiableList(locations);
    }
}
