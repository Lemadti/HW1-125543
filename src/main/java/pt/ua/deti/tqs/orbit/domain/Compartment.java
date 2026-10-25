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

    protected Compartment() {
    }

    public Compartment(Station station, String code, int position, CompartmentSize size) {
        this.station = station;
        this.code = code;
        this.position = position;
        this.size = size;
    }

    public Long getId() {
        return id;
    }

    public Station getStation() {
        return station;
    }

    public String getCode() {
        return code;
    }

    public int getPosition() {
        return position;
    }

    public CompartmentSize getSize() {
        return size;
    }

    public CompartmentStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public boolean isInService() {
        return status == CompartmentStatus.IN_SERVICE;
    }

    public void putOutOfService(String reason) {
        this.status = CompartmentStatus.OUT_OF_SERVICE;
        this.statusReason = reason;
    }

    public void putInService() {
        this.status = CompartmentStatus.IN_SERVICE;
        this.statusReason = null;
    }
}
