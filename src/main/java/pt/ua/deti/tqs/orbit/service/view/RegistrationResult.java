package pt.ua.deti.tqs.orbit.service.view;

import java.math.BigDecimal;
import java.time.Instant;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.ParcelState;

/**
 * Returned to the partner when a parcel is registered (UC2). The delivery code (QR) and the drop-off PIN are meant
 * to be printed on the box label; they are returned only once and never stored in clear (the PIN).
 */
public record RegistrationResult(String trackingCode, ParcelState state, String stationCode, String stationName,
                                 String compartmentCode, CompartmentSize size, Instant leaseStart, int leaseHours,
                                 Instant dropOffDeadline, BigDecimal price, String currency, String deliveryCode,
                                 String dropOffCode) {
}
