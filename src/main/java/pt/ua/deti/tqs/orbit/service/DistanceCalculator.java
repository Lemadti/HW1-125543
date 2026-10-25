package pt.ua.deti.tqs.orbit.service;

import pt.ua.deti.tqs.orbit.domain.GeoPoint;

/** Great-circle distance with the haversine formula, and a walking-time estimate. */
public final class DistanceCalculator {

    /** Mean Earth radius (IUGG), in kilometres. */
    static final double EARTH_RADIUS_KM = 6371.0088;

    private DistanceCalculator() {
    }

    /**
     * Haversine distance between two points.
     *
     * @param a first point
     * @param b second point
     * @return distance in kilometres
     */
    public static double distanceKm(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /**
     * Walking time at 5 km/h, rounded up (at least one minute).
     *
     * @param km distance in kilometres
     * @return minutes
     */
    public static int walkingMinutes(double km) {
        return Math.max(1, (int) Math.ceil(km / 5.0 * 60));
    }
}
