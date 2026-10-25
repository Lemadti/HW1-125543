package pt.ua.deti.tqs.orbit.service.view;

/**
 * A station with its free compartments per size. {@code distanceKm} / {@code walkingMinutes} are null when the list
 * is not ranked by distance (e.g. geocoding unavailable).
 */
public record StationSummary(String code, String name, String address, String postalCode, String city,
                             double latitude, double longitude, String accessNotes, long totalCompartments,
                             SizeCounts free, Double distanceKm, Integer walkingMinutes) {
}
