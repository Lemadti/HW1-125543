package pt.ua.deti.tqs.orbit.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;

/** Price of a reservation: one flat price for every parcel ({@code orbit.rules.price}), VAT included. */
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
     * Price of a reservation, the same whatever the station, size or compartment.
     *
     * @return the price, with two decimals
     */
    public BigDecimal price() {
        return rules.price().setScale(2, RoundingMode.HALF_UP);
    }
}
