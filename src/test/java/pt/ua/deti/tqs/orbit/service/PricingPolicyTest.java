package pt.ua.deti.tqs.orbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.support.TestProperties;

class PricingPolicyTest {

    private final PricingPolicy pricing = new PricingPolicy(TestProperties.defaults());

    @ParameterizedTest
    @CsvSource({"24, 3.00", "48, 6.00", "72, 9.00"})
    void pricePerStartedDay(int hours, String price) {
        assertThat(pricing.price(hours)).isEqualByComparingTo(new BigDecimal(price));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 12, 36, 96})
    void onlyConfiguredDurationsAreAccepted(int hours) {
        assertThatThrownBy(() -> pricing.validateLeaseHours(hours))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_LEASE_DURATION"));
    }
}
