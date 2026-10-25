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

/** A malfunction report shown to the operator. */
@Entity
@Table(name = "incident")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compartment_id")
    private Compartment compartment;

    @Column(name = "tracking_code")
    private String trackingCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "reported_by", nullable = false)
    private Actor reportedBy;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    private String description;

    /** Constructor for JPA. */
    protected Incident() {
    }

    /**
     * Creates an incident.
     *
     * @param compartment  the compartment that misbehaved
     * @param trackingCode the parcel involved, or null
     * @param type         kind of malfunction
     * @param reportedBy   who reported it
     * @param reportedAt   when it was reported
     * @param description  free-text description
     */
    public Incident(Compartment compartment, String trackingCode, IncidentType type, Actor reportedBy,
                    Instant reportedAt, String description) {
        this.compartment = compartment;
        this.trackingCode = trackingCode;
        this.type = type;
        this.reportedBy = reportedBy;
        this.reportedAt = reportedAt;
        this.description = description;
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
     * Returns the compartment.
     *
     * @return the compartment
     */
    public Compartment getCompartment() {
        return compartment;
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
     * Returns the type.
     *
     * @return the type
     */
    public IncidentType getType() {
        return type;
    }

    /**
     * Returns the reported by.
     *
     * @return the reported by
     */
    public Actor getReportedBy() {
        return reportedBy;
    }

    /**
     * Returns the reported at.
     *
     * @return the reported at
     */
    public Instant getReportedAt() {
        return reportedAt;
    }

    /**
     * Returns the description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }
}
