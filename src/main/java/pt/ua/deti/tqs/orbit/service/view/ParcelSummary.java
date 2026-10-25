package pt.ua.deti.tqs.orbit.service.view;

import java.time.Instant;
import pt.ua.deti.tqs.orbit.domain.ParcelState;

/**
 * One row of the operator parcel lists.
 *
 * @param trackingCode    tracking code
 * @param state           current state
 * @param stationCode     station code
 * @param stationName     station name
 * @param compartmentCode compartment
 * @param senderName      sender name
 * @param recipientName   recipient name
 * @param createdAt       registration time
 * @param dropOffDeadline end of the lease
 * @param pickupDeadline  end of the pickup window, or null
 */
public record ParcelSummary(String trackingCode, ParcelState state, String stationCode, String stationName,
                            String compartmentCode, String senderName, String recipientName, Instant createdAt,
                            Instant dropOffDeadline, Instant pickupDeadline) {
}
