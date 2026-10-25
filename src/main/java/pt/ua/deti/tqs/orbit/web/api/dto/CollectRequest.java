package pt.ua.deti.tqs.orbit.web.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The courier scans the QR code (delivery code) of the box.
 *
 * @param deliveryCode  QR content, e.g. DLV-UMS2AMJBRPED
 * @param locationLabel optional location of the collection
 */
public record CollectRequest(@NotBlank String deliveryCode, @Size(max = 200) String locationLabel) {
}
