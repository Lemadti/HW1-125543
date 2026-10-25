package pt.ua.deti.tqs.orbit.service.view;

import java.util.List;

/** Result of UC1: stations within the radius of the origin, nearest first. */
public record NearbySearchResult(String query, double originLatitude, double originLongitude, double radiusKm,
                                 List<StationSummary> stations) {
}
