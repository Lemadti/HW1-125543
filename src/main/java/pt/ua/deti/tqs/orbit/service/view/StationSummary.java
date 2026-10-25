package pt.ua.deti.tqs.orbit.service.view;

/**
 * A station with its free compartments per size.
 *
 * @param code              station code
 * @param name              station name
 * @param address           street address
 * @param postalCode        postal code
 * @param city              city or municipality
 * @param latitude          WGS84 latitude
 * @param longitude         WGS84 longitude
 * @param accessNotes       how to find the lockers on site
 * @param totalCompartments number of compartments
 * @param free              free compartments per size
 * @param distanceKm        distance from the search origin, or null when the list is not ranked by distance
 * @param walkingMinutes    walking time from the search origin, or null
 */
public record StationSummary(String code, String name, String address, String postalCode, String city,
                                double latitude, double longitude, String accessNotes, long totalCompartments,
                                SizeCounts free, Double distanceKm, Integer walkingMinutes) {
}
