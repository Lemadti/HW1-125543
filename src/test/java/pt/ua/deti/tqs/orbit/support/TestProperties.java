package pt.ua.deti.tqs.orbit.support;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;

/** The default business rules (same values as application.yml) for unit tests without Spring. */
public final class TestProperties {

    private TestProperties() {
    }

    public static OrbitProperties defaults() {
        return new OrbitProperties(
                new OrbitProperties.Rules(List.of(24, 48, 72), 48, new BigDecimal("12.00"), "PLN", Duration.ofDays(15),
                        Duration.ofDays(7), Duration.ofMinutes(5)),
                new OrbitProperties.Codes(6, 3, Duration.ofHours(1), "test-secret"),
                new OrbitProperties.Search(5.0, 10),
                new OrbitProperties.Geocoding("stub", "http://localhost", "test", "pl", Duration.ofSeconds(1),
                        Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofHours(24), Duration.ofHours(1), 100),
                new OrbitProperties.Hardware(false),
                new OrbitProperties.Demo(false, false),
                "+48 22 100 20 30");
    }
}
