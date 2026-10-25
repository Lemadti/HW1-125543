package pt.ua.deti.tqs.orbit.service.view;

import java.util.List;

/**
 * Result of UC1: stations within the radius of the origin, nearest first.
 *
 * @param query           the address searched, or null for a coordinate search
 * @param originLatitude  latitude of the origin
 * @param originLongitude longitude of the origin
 * @param radiusKm        search radius
 * @param stations        the ranked stations
 */
public record NearbySearchResult(String query, double originLatitude, double originLongitude, double radiusKm,
                                    List<StationSummary> stations) {
}
