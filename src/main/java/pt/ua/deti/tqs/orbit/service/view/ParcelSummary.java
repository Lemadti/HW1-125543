package pt.ua.deti.tqs.orbit.service.view;

import java.time.Instant;
import pt.ua.deti.tqs.orbit.domain.ParcelState;

/** One row of the operator parcel lists. */
public record ParcelSummary(String trackingCode, ParcelState state, String stationCode, String stationName,
                            String compartmentCode, String senderName, String recipientName, Instant createdAt,
                            Instant dropOffDeadline, Instant pickupDeadline) {
}
