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

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** End of the drop-off window: the courier must collect and drop off the parcel until then (inclusive). */
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

    /** Constructor for JPA. */
    protected Parcel() {
    }

    /**
     * Registers a parcel: initial state CREATED, compartment reserved and the first transition recorded.
     *
     * @param trackingCode    public tracking code
     * @param deliveryCode    QR code content for the label
     * @param compartment     the reserved compartment (its station and size are copied)
     * @param sender          sender contact
     * @param recipient       recipient contact
     * @param dropOffDeadline end of the drop-off window
     * @param price           price charged
     * @param dropOffCodeHash HMAC of the drop-off PIN
     * @param now             registration time
     * @return the new parcel
     */
    public static Parcel register(String trackingCode, String deliveryCode, Compartment compartment,
                                    Contact sender, Contact recipient, Instant dropOffDeadline,
                                    BigDecimal price, String dropOffCodeHash, Instant now) {
        Parcel p = new Parcel();
        p.trackingCode = trackingCode;
        p.deliveryCode = deliveryCode;
        p.station = compartment.getStation();
        p.compartment = compartment;
        p.size = compartment.getSize();
        p.sender = sender;
        p.recipient = recipient;
        p.price = price;
        p.createdAt = now;
        p.dropOffDeadline = dropOffDeadline;
        p.dropOffCodeHash = dropOffCodeHash;
        p.state = ParcelState.CREATED;
        p.history.add(new ParcelTransition(p, null, ParcelState.CREATED, null, Actor.PARTNER, now,
                "Registered, compartment " + compartment.getCode() + " reserved"));
        return p;
    }

    /**
     * Applies a lifecycle event, validated by {@link ParcelStateMachine}, and records the transition.
     *
     * @param event the event
     * @param actor who triggers it
     * @param at    when it happens
     * @param note  optional note for the history
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

    /**
     * Records the deposit: sets the pickup deadline and the pickup code, and invalidates the drop-off code.
     *
     * @param at             deposit time
     * @param pickupDeadline end of the pickup window
     * @param pickupCodeHash HMAC of the pickup PIN
     */
    public void markDeposited(Instant at, Instant pickupDeadline, String pickupCodeHash) {
        this.depositedAt = at;
        this.pickupDeadline = pickupDeadline;
        this.pickupCodeHash = pickupCodeHash;
        this.dropOffCodeHash = null;
    }

    /**
     * Moves the reservation to another compartment of the same size (hardware failure or out of service).
     *
     * @param other              the new compartment
     * @param newDropOffCodeHash HMAC of the new drop-off PIN
     * @throws IllegalArgumentException if the compartment size differs
     */
    public void relocateTo(Compartment other, String newDropOffCodeHash) {
        if (other.getSize() != size) {
            throw new IllegalArgumentException("Compartment size mismatch");
        }
        this.compartment = other;
        this.dropOffCodeHash = newDropOffCodeHash;
    }

    /** Invalidates the pickup code (single use). */
    public void consumePickupCode() {
        this.pickupCodeHash = null;
    }

    /**
     * Starts a terminal interaction (door opened, waiting for confirmation).
     *
     * @param token     random session token
     * @param expiresAt when the session expires
     */
    public void openTerminalSession(String token, Instant expiresAt) {
        this.terminalSession = token;
        this.terminalSessionExpiresAt = expiresAt;
    }

    /**
     * Whether the given token is the current, unexpired terminal session.
     *
     * @param token token presented by the terminal
     * @param now   current time
     * @return true if valid
     */
    public boolean hasTerminalSession(String token, Instant now) {
        return terminalSession != null && terminalSession.equals(token)
                && terminalSessionExpiresAt != null && now.isBefore(terminalSessionExpiresAt);
    }

    /**
     * Whether a terminal interaction is in progress (a door was opened and not yet confirmed closed).
     *
     * @param now current time
     * @return true if there is an unexpired terminal session
     */
    public boolean hasOpenTerminalSession(Instant now) {
        return terminalSession != null && terminalSessionExpiresAt != null && now.isBefore(terminalSessionExpiresAt);
    }

    /** Ends the current terminal interaction. */
    public void clearTerminalSession() {
        this.terminalSession = null;
        this.terminalSessionExpiresAt = null;
    }

    /**
     * Adds a courier location scan.
     *
     * @param label     location description
     * @param latitude  optional latitude
     * @param longitude optional longitude
     * @param at        scan time
     * @return the new scan
     */
    public LocationScan addLocation(String label, Double latitude, Double longitude, Instant at) {
        LocationScan scan = new LocationScan(this, label, latitude, longitude, at);
        locations.add(scan);
        return scan;
    }

    /**
     * The most recent location scan.
     *
     * @return the last scan, or empty if there is none
     */
    public Optional<LocationScan> lastLocation() {
        return locations.isEmpty() ? Optional.empty() : Optional.of(locations.get(locations.size() - 1));
    }

    /**
     * Returns the ID.
     *
     * @return the ID
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns the tracking code.
     *
     * @return the tracking code
     */
    public String getTrackingCode() {
        return trackingCode;
    }

    /**
     * Returns the delivery code.
     *
     * @return the delivery code
     */
    public String getDeliveryCode() {
        return deliveryCode;
    }

    /**
     * Returns the station.
     *
     * @return the station
     */
    public Station getStation() {
        return station;
    }

    /**
     * Returns the compartment.
     *
     * @return the compartment
     */
    public Compartment getCompartment() {
        return compartment;
    }

    /**
     * Returns the size.
     *
     * @return the size
     */
    public CompartmentSize getSize() {
        return size;
    }

    /**
     * Returns the state.
     *
     * @return the state
     */
    public ParcelState getState() {
        return state;
    }

    /**
     * Returns the sender.
     *
     * @return the sender
     */
    public Contact getSender() {
        return sender;
    }

    /**
     * Returns the recipient.
     *
     * @return the recipient
     */
    public Contact getRecipient() {
        return recipient;
    }

    /**
     * Returns the price.
     *
     * @return the price
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * Returns the created at.
     *
     * @return the created at
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the drop off deadline.
     *
     * @return the drop off deadline
     */
    public Instant getDropOffDeadline() {
        return dropOffDeadline;
    }

    /**
     * Returns the deposited at.
     *
     * @return the deposited at
     */
    public Instant getDepositedAt() {
        return depositedAt;
    }

    /**
     * Returns the pickup deadline.
     *
     * @return the pickup deadline
     */
    public Instant getPickupDeadline() {
        return pickupDeadline;
    }

    /**
     * Returns the drop off code hash.
     *
     * @return the drop off code hash
     */
    public String getDropOffCodeHash() {
        return dropOffCodeHash;
    }

    /**
     * Returns the pickup code hash.
     *
     * @return the pickup code hash
     */
    public String getPickupCodeHash() {
        return pickupCodeHash;
    }

    /**
     * Returns the history.
     *
     * @return the history
     */
    public List<ParcelTransition> getHistory() {
        return Collections.unmodifiableList(history);
    }

    /**
     * Returns the locations.
     *
     * @return the locations
     */
    public List<LocationScan> getLocations() {
        return Collections.unmodifiableList(locations);
    }
}
