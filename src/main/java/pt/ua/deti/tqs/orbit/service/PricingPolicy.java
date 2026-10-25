package pt.ua.deti.tqs.orbit.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.domain.OrbitException;

/** Lease options and price: price per started day (24 h) of lease, VAT included. */
@Component
public class PricingPolicy {

    private final OrbitProperties.Rules rules;

    public PricingPolicy(OrbitProperties properties) {
        this.rules = properties.rules();
    }

    public void validateLeaseHours(int hours) {
        if (!rules.leaseHoursOptions().contains(hours)) {
            throw OrbitException.unprocessable("INVALID_LEASE_DURATION",
                    "Lease duration must be one of " + rules.leaseHoursOptions() + " hours");
        }
    }

    public BigDecimal price(int leaseHours) {
        int days = (leaseHours + 23) / 24;
        return rules.pricePerDay().multiply(BigDecimal.valueOf(days)).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
