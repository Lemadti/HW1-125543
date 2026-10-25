package pt.ua.deti.tqs.orbit.web.api.dto;

import jakarta.validation.constraints.Size;
import pt.ua.deti.tqs.orbit.domain.Actor;

/**
 * Body of a cancellation.
 *
 * @param actor  PARTNER (default) or RECIPIENT
 * @param reason optional reason, up to 200 characters
 */
public record CancelRequest(Actor actor, @Size(max = 200) String reason) {
}
