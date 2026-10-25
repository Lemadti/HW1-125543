package pt.ua.deti.tqs.orbit.adapter.hardware;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.port.LockerHardwarePort;

/**
 * Wires the hardware port. Real devices are out of scope, so the fake is the only adapter; with
 * {@code orbit.hardware.auto-close=true} it behaves as a working locker (demo configuration).
 */
@Configuration
public class HardwareConfig {

    @Bean
    FakeLockerHardware fakeLockerHardware(OrbitProperties properties) {
        return new FakeLockerHardware(properties.hardware().autoClose());
    }

    @Bean
    @Primary
    LockerHardwarePort lockerHardwarePort(FakeLockerHardware fake) {
        return new LoggingLockerHardware(fake);
    }
}
