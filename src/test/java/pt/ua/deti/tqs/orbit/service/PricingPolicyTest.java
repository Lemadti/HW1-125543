package pt.ua.deti.tqs.orbit.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.support.TestProperties;

@DisplayName("PricingPolicy — flat price")
class PricingPolicyTest {

    private final PricingPolicy pricing = new PricingPolicy(TestProperties.defaults());

    @Test
    @DisplayName("Price: every reservation costs the configured flat 3.00 EUR")
    void everyReservationCostsTheConfiguredFlatPrice() {
        assertThat(pricing.price()).isEqualByComparingTo(new BigDecimal("3.00")).hasScaleOf(2);
    }
}
