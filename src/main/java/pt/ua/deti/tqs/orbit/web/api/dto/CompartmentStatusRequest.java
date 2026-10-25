package pt.ua.deti.tqs.orbit.web.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pt.ua.deti.tqs.orbit.domain.CompartmentStatus;

public record CompartmentStatusRequest(@NotNull CompartmentStatus status, @Size(max = 200) String reason) {
}
