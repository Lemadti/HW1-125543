package pt.ua.deti.tqs.orbit.service;

import pt.ua.deti.tqs.orbit.domain.GeoPoint;

/** Great-circle distance with the haversine formula. */
public final class DistanceCalculator {

    static final double EARTH_RADIUS_KM = 6371.0088;

    private DistanceCalculator() {
    }

    public static double distanceKm(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /** Walking time estimate at 5 km/h, rounded up to whole minutes (at least 1). */
    public static int walkingMinutes(double km) {
        return Math.max(1, (int) Math.ceil(km / 5.0 * 60));
    }
}
