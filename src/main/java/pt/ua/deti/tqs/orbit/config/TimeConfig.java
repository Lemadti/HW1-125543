package pt.ua.deti.tqs.orbit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Time is injected everywhere as a {@link java.time.Clock} bean, so time-based rules are testable without sleeping.
 * The bean is a {@link MutableClock} on system time; only tests and the demo endpoints (disabled by default) move it.
 */
@Configuration
public class TimeConfig {

    @Bean
    MutableClock clock() {
        return MutableClock.system();
    }
}
