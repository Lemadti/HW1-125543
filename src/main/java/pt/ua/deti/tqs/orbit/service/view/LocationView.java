package pt.ua.deti.tqs.orbit.service.view;

import java.time.Instant;

/**
 * A courier location scan.
 *
 * @param label     location description
 * @param latitude  latitude, or null
 * @param longitude longitude, or null
 * @param at        scan time
 */
public record LocationView(String label, Double latitude, Double longitude, Instant at) {
}
