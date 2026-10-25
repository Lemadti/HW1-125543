package pt.ua.deti.tqs.orbit.service.view;

import java.time.Instant;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.IncidentType;

/**
 * A malfunction report shown to the operator.
 *
 * @param id              incident id
 * @param compartmentCode compartment that misbehaved
 * @param trackingCode    parcel involved, or null
 * @param type            kind of malfunction
 * @param reportedBy      who reported it
 * @param reportedAt      when it was reported
 * @param description     free-text description
 */
public record IncidentView(Long id, String compartmentCode, String trackingCode, IncidentType type, Actor reportedBy,
                            Instant reportedAt, String description) {
}
