package pt.ua.deti.tqs.orbit.service.view;

import java.time.Instant;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.IncidentType;

public record IncidentView(Long id, String compartmentCode, String trackingCode, IncidentType type, Actor reportedBy,
                           Instant reportedAt, String description) {
}
