package pt.ua.deti.tqs.orbit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** A location reported by the courier while the parcel is ON_ITS_WAY (e.g. "Aveiro depot · Esgueira"). */
@Entity
@Table(name = "location_scan")
public class LocationScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parcel_id")
    private Parcel parcel;

    @Column(nullable = false)
    private String label;

    private Double latitude;

    private Double longitude;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    /** Constructor for JPA. */
    protected LocationScan() {
    }

    /**
     * Creates a scan (see {@link Parcel#addLocation}).
     *
     * @param parcel    the parcel
     * @param label     location description
     * @param latitude  optional latitude
     * @param longitude optional longitude
     * @param scannedAt scan time
     */
    LocationScan(Parcel parcel, String label, Double latitude, Double longitude, Instant scannedAt) {
        this.parcel = parcel;
        this.label = label;
        this.latitude = latitude;
        this.longitude = longitude;
        this.scannedAt = scannedAt;
    }

    /**
     * Returns the label.
     *
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns the latitude.
     *
     * @return the latitude
     */
    public Double getLatitude() {
        return latitude;
    }

    /**
     * Returns the longitude.
     *
     * @return the longitude
     */
    public Double getLongitude() {
        return longitude;
    }

    /**
     * Returns the scanned at.
     *
     * @return the scanned at
     */
    public Instant getScannedAt() {
        return scannedAt;
    }
}
