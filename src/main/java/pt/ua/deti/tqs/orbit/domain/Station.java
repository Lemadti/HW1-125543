package pt.ua.deti.tqs.orbit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A locker station (seed data). */
@Entity
@Table(name = "station")
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public station code, e.g. ORB-AVR-001. */
    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "access_notes")
    private String accessNotes;

    /** Constructor for JPA. */
    protected Station() {
    }

    /**
     * Creates a station.
     *
     * @param code        public station code, e.g. ORB-AVR-001
     * @param name        display name, e.g. "Fórum Aveiro"
     * @param address     street address
     * @param postalCode  Portuguese postal code, e.g. 3810-064
     * @param city        city or municipality
     * @param latitude    WGS84 latitude
     * @param longitude   WGS84 longitude
     * @param accessNotes how to find the lockers on site (may be null)
     */
    public Station(String code, String name, String address, String postalCode, String city,
                   double latitude, double longitude, String accessNotes) {
        this.code = code;
        this.name = name;
        this.address = address;
        this.postalCode = postalCode;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accessNotes = accessNotes;
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
     * Returns the code.
     *
     * @return the code
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the address.
     *
     * @return the address
     */
    public String getAddress() {
        return address;
    }

    /**
     * Returns the postal code.
     *
     * @return the postal code
     */
    public String getPostalCode() {
        return postalCode;
    }

    /**
     * Returns the city.
     *
     * @return the city
     */
    public String getCity() {
        return city;
    }

    /**
     * Returns the latitude.
     *
     * @return the latitude
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * Returns the longitude.
     *
     * @return the longitude
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * The station coordinates.
     *
     * @return latitude and longitude as a point
     */
    public GeoPoint location() {
        return new GeoPoint(latitude, longitude);
    }

    /**
     * Returns the access notes.
     *
     * @return the access notes
     */
    public String getAccessNotes() {
        return accessNotes;
    }
}
