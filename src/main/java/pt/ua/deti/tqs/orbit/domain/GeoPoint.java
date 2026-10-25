package pt.ua.deti.tqs.orbit.domain;

/**
 * WGS84 coordinates.
 *
 * @param latitude  latitude in degrees, between -90 and 90
 * @param longitude longitude in degrees, between -180 and 180
 */
public record GeoPoint(double latitude, double longitude) {

    /**
     * Validates the coordinates.
     *
     * @throws IllegalArgumentException if a value is out of range
     */
    public GeoPoint {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
    }
}
