package pt.ua.deti.tqs.orbit.service.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;

/**
 * Full parcel information for the operator.
 *
 * @param trackingCode    tracking code
 * @param state           current state
 * @param stationCode     station code
 * @param stationName     station name
 * @param compartmentCode reserved compartment
 * @param size            parcel size
 * @param sender          sender contact
 * @param recipient       recipient contact
 * @param createdAt       registration time
 * @param price           price charged
 * @param dropOffDeadline end of the drop-off window
 * @param depositedAt     drop-off time, or null
 * @param pickupDeadline  end of the pickup window, or null
 * @param lastLocation    last courier scan, or null
 * @param history         every transition, oldest first
 * @param allowedEvents   events valid in the current state
 */
public record ParcelDetail(String trackingCode, ParcelState state, String stationCode, String stationName,
                            String compartmentCode, CompartmentSize size, ContactView sender,
                            ContactView recipient, Instant createdAt,
                            BigDecimal price, Instant dropOffDeadline, Instant depositedAt, Instant pickupDeadline,
                            LocationView lastLocation, List<TransitionView> history,
                            List<ParcelEvent> allowedEvents) {

    /**
     * Contact details shown to the operator.
     *
     * @param name  full name
     * @param email e-mail address
     * @param phone phone number
     */
    public record ContactView(String name, String email, String phone) {
    }
}
