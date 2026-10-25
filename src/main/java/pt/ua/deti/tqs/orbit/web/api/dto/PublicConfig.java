package pt.ua.deti.tqs.orbit.web.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Business parameters the web pages need to render forms.
 *
 * @param leaseHoursOptions allowed lease durations in hours
 * @param defaultLeaseHours default lease duration
 * @param pricePerDay       price per started day of lease
 * @param currency          currency code (EUR)
 * @param pickupWindowDays  days allowed for pickup after drop-off
 * @param radiusOptionsKm   radius choices offered in the search box (plus "any distance")
 * @param maxRadiusKm       largest radius a client may request
 * @param assistancePhone   phone number shown on the terminals
 * @param demoEnabled       whether the demo tools are available
 */
public record PublicConfig(List<Integer> leaseHoursOptions, int defaultLeaseHours, BigDecimal pricePerDay,
                           String currency, long pickupWindowDays, List<Double> radiusOptionsKm, double maxRadiusKm,
                           String assistancePhone,
                           boolean demoEnabled) {
}
