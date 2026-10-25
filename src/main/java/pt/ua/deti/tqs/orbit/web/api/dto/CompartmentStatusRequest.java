package pt.ua.deti.tqs.orbit.web.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pt.ua.deti.tqs.orbit.domain.CompartmentStatus;

/**
 * New status of a compartment.
 *
 * @param status IN_SERVICE or OUT_OF_SERVICE
 * @param reason optional reason, up to 200 characters
 */
public record CompartmentStatusRequest(@NotNull CompartmentStatus status, @Size(max = 200) String reason) {
}
