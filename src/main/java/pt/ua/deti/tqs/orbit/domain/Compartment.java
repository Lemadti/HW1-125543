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

/** A compartment of a station. Code is e.g. "M-07" (size prefix + position in the bank). */
@Entity
@Table(name = "compartment")
public class Compartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id")
    private Station station;

    @Column(nullable = false)
    private String code;

    /** Position in the bank (1-based); the front view grid has 4 rows per column. */
    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompartmentSize size;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompartmentStatus status = CompartmentStatus.IN_SERVICE;

    @Column(name = "status_reason")
    private String statusReason;

    /** Constructor for JPA. */
    protected Compartment() {
    }

    /**
     * Creates an in-service compartment.
     *
     * @param station  the station it belongs to
     * @param code     display code, e.g. "M-07"
     * @param position 1-based position in the bank
     * @param size     compartment size
     */
    public Compartment(Station station, String code, int position, CompartmentSize size) {
        this.station = station;
        this.code = code;
        this.position = position;
        this.size = size;
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
     * Returns the station.
     *
     * @return the station
     */
    public Station getStation() {
        return station;
    }

    /**
     * Returns the code.
     *
     * @return the code
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the position.
     *
     * @return the position
     */
    public int getPosition() {
        return position;
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
     * Returns the status.
     *
     * @return the status
     */
    public CompartmentStatus getStatus() {
        return status;
    }

    /**
     * Returns the status reason.
     *
     * @return the status reason
     */
    public String getStatusReason() {
        return statusReason;
    }

    /**
     * Whether this is in service.
     *
     * @return true if in service
     */
    public boolean isInService() {
        return status == CompartmentStatus.IN_SERVICE;
    }

    /**
     * Marks the compartment as faulty: no new parcel will be allocated to it.
     *
     * @param reason why it was put out of service
     */
    public void putOutOfService(String reason) {
        this.status = CompartmentStatus.OUT_OF_SERVICE;
        this.statusReason = reason;
    }

    /** Puts the compartment back in service and clears the reason. */
    public void putInService() {
        this.status = CompartmentStatus.IN_SERVICE;
        this.statusReason = null;
    }
}
