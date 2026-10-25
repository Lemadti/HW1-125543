package pt.ua.deti.tqs.orbit.web.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A PIN typed at the terminal.
 *
 * @param code the PIN (6 digits expected)
 */
public record CodeRequest(@NotBlank @Size(max = 12) String code) {
}
