package pt.ua.deti.tqs.orbit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point of Orbit Lockers.
 *
 * <p>Enables configuration-properties scanning (business rules under {@code orbit.*}) and scheduling (the deadline
 * job in {@link pt.ua.deti.tqs.orbit.service.DeadlineService}).</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class OrbitApplication {

    /** Created by Spring; use {@link #main(String[])} to start the application. */
    public OrbitApplication() {
        // no state
    }

    /**
     * Starts the application.
     *
     * @param args command-line arguments, passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(OrbitApplication.class, args);
    }
}
