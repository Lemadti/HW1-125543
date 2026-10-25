package pt.ua.deti.tqs.orbit.web.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Courier scans the QR code (delivery code) of the box. */
public record CollectRequest(@NotBlank String deliveryCode, @Size(max = 200) String locationLabel) {
}
