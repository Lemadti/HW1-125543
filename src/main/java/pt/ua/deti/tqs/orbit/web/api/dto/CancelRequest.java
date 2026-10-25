package pt.ua.deti.tqs.orbit.web.api.dto;

import jakarta.validation.constraints.Size;
import pt.ua.deti.tqs.orbit.domain.Actor;

/** @param actor PARTNER (default) or RECIPIENT */
public record CancelRequest(Actor actor, @Size(max = 200) String reason) {
}
