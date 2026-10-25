package pt.ua.deti.tqs.orbit.service.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;

/** Full parcel information for the operator. */
public record ParcelDetail(String trackingCode, ParcelState state, String stationCode, String stationName,
                           String compartmentCode, CompartmentSize size, ContactView sender,
                           ContactView recipient, Instant createdAt, Instant leaseStart, int leaseHours,
                           BigDecimal price, Instant dropOffDeadline, Instant depositedAt, Instant pickupDeadline,
                           LocationView lastLocation, List<TransitionView> history,
                           List<ParcelEvent> allowedEvents) {

    public record ContactView(String name, String email, String phone) {
    }
}
