package pt.ua.deti.tqs.orbit.service.view;

import java.time.Instant;
import java.util.List;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;

/** Public tracking information (UC5). Personal data is masked. */
public record TrackingView(String trackingCode, ParcelState state, String stationCode, String stationName,
                           String stationAddress, double stationLatitude, double stationLongitude,
                           String compartmentCode, CompartmentSize size, String recipientName,
                           Instant createdAt, Instant leaseStart, int leaseHours, Instant dropOffDeadline,
                           Instant depositedAt, Instant pickupDeadline, LocationView lastLocation,
                           List<LocationView> locations, List<TransitionView> history,
                           List<ParcelEvent> allowedEvents) {
}
