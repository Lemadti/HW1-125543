package pt.ua.deti.tqs.orbit.web.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A courier location update.
 *
 * @param label     location description (required)
 * @param latitude  optional latitude
 * @param longitude optional longitude
 */
public record LocationRequest(
        @NotBlank @Size(max = 200) String label,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude) {
}
