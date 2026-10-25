package pt.ua.deti.tqs.orbit.web.api.dto;

import java.math.BigDecimal;
import java.util.List;

/** Business parameters the web pages need to render forms. */
public record PublicConfig(List<Integer> leaseHoursOptions, int defaultLeaseHours, BigDecimal pricePerDay,
                           String currency, long pickupWindowDays, double searchRadiusKm, String assistancePhone,
                           boolean demoEnabled) {
}
