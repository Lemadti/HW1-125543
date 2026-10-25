package pt.ua.deti.tqs.orbit.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.domain.OrbitException;

/** Lease options and price: price per started day (24 h) of lease, VAT included. */
@Component
public class PricingPolicy {

    private final OrbitProperties.Rules rules;

    /**
     * Creates the policy from {@code orbit.rules.*}.
     *
     * @param properties business rules
     */
    public PricingPolicy(OrbitProperties properties) {
        this.rules = properties.rules();
    }

    /**
     * Checks that the duration is one of the configured options.
     *
     * @param hours lease duration
     * @throws OrbitException INVALID_LEASE_DURATION
     */
    public void validateLeaseHours(int hours) {
        if (!rules.leaseHoursOptions().contains(hours)) {
            throw OrbitException.unprocessable("INVALID_LEASE_DURATION",
                    "Lease duration must be one of " + rules.leaseHoursOptions() + " hours");
        }
    }

    /**
     * Price per started day of lease.
     *
     * @param leaseHours lease duration in hours
     * @return the price, with two decimals
     */
    public BigDecimal price(int leaseHours) {
        int days = (leaseHours + 23) / 24;
        return rules.pricePerDay().multiply(BigDecimal.valueOf(days)).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
