package pt.ua.deti.tqs.orbit.service.view;

import java.time.Instant;

public record LocationView(String label, Double latitude, Double longitude, Instant at) {
}
