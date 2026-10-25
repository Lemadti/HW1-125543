package pt.ua.deti.tqs.orbit.service.view;

import java.time.Instant;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;

/**
 * A compartment as shown in the station front view.
 *
 * @param availability AVAILABLE, RESERVED or OUT_OF_SERVICE
 * @param reservedUntil for RESERVED: until when the current parcel may hold it (drop-off or pickup deadline)
 * @param column 0-based column in the bank
 * @param row 0-based row in the bank
 */
public record CompartmentView(String code, int position, int column, int row, CompartmentSize size,
                              int heightCm, int widthCm, int depthCm, String availability, Instant reservedUntil,
                              String statusReason) {
}
