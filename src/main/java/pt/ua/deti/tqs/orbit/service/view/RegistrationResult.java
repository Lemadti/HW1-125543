package pt.ua.deti.tqs.orbit.service.view;

import java.math.BigDecimal;
import java.time.Instant;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.ParcelState;

/**
 * Returned to the partner when a parcel is registered (UC2). The delivery code (QR) and the drop-off PIN are
 * meant to be printed on the box label; they are returned only once and the PIN is never stored in clear.
 *
 * @param trackingCode    tracking code
 * @param state           CREATED
 * @param stationCode     station code
 * @param stationName     station name
 * @param compartmentCode reserved compartment
 * @param size            parcel size
 * @param leaseStart      start of the lease
 * @param leaseHours      lease duration
 * @param dropOffDeadline end of the lease
 * @param price           price charged
 * @param currency        currency code
 * @param deliveryCode    QR content for the label
 * @param dropOffCode     6-digit drop-off PIN for the label
 */
public record RegistrationResult(String trackingCode, ParcelState state, String stationCode, String stationName,
                                    String compartmentCode, CompartmentSize size, Instant leaseStart, int leaseHours,
                                    Instant dropOffDeadline, BigDecimal price, String currency, String deliveryCode,
                                    String dropOffCode) {
}
