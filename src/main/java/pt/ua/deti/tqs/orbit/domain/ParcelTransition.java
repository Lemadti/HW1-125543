package pt.ua.deti.tqs.orbit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** One entry of the parcel history: every transition is recorded with its timestamp and actor. */
@Entity
@Table(name = "parcel_transition")
public class ParcelTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parcel_id")
    private Parcel parcel;

    /** Null for the initial registration. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_state")
    private ParcelState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false)
    private ParcelState toState;

    /** Null for the initial registration. */
    @Enumerated(EnumType.STRING)
    private ParcelEvent event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Actor actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    private String note;

    protected ParcelTransition() {
    }

    ParcelTransition(Parcel parcel, ParcelState fromState, ParcelState toState, ParcelEvent event, Actor actor,
                     Instant occurredAt, String note) {
        this.parcel = parcel;
        this.fromState = fromState;
        this.toState = toState;
        this.event = event;
        this.actor = actor;
        this.occurredAt = occurredAt;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public ParcelState getFromState() {
        return fromState;
    }

    public ParcelState getToState() {
        return toState;
    }

    public ParcelEvent getEvent() {
        return event;
    }

    public Actor getActor() {
        return actor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getNote() {
        return note;
    }
}
