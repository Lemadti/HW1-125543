package pt.ua.deti.tqs.orbit.service.view;

import java.time.Instant;
import java.util.List;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;

/**
 * Public tracking information (UC5). Personal data is masked.
 *
 * @param trackingCode     tracking code
 * @param state            current state
 * @param stationCode      station code
 * @param stationName      station name
 * @param stationAddress   full station address
 * @param stationLatitude  station latitude
 * @param stationLongitude station longitude
 * @param compartmentCode  reserved compartment
 * @param size             parcel size
 * @param recipientName    recipient name with the last name as an initial
 * @param createdAt        registration time
 * @param dropOffDeadline  end of the drop-off window
 * @param depositedAt      drop-off time, or null
 * @param pickupDeadline   end of the pickup window, or null
 * @param lastLocation     last courier scan, or null
 * @param locations        every courier scan, oldest first
 * @param history          every transition, oldest first
 * @param allowedEvents    events valid in the current state
 */
public record TrackingView(String trackingCode, ParcelState state, String stationCode, String stationName,
                            String stationAddress, double stationLatitude, double stationLongitude,
                            String compartmentCode, CompartmentSize size, String recipientName,
                            Instant createdAt, Instant dropOffDeadline,
                            Instant depositedAt, Instant pickupDeadline, LocationView lastLocation,
                            List<LocationView> locations, List<TransitionView> history,
                            List<ParcelEvent> allowedEvents) {
}
