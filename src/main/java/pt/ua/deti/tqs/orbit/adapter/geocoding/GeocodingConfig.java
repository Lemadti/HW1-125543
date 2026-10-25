package pt.ua.deti.tqs.orbit.adapter.geocoding;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.port.GeocoderPort;

/** Selects the geocoding adapter with {@code orbit.geocoding.provider} (nominatim | stub). */
@Configuration
public class GeocodingConfig {

    private static final Logger log = LoggerFactory.getLogger(GeocodingConfig.class);

    /** Creates the geocoding configuration. */
    public GeocodingConfig() {
        // no state
    }

    /**
     * Builds the geocoder selected by {@code orbit.geocoding.provider}: the stub, or Nominatim behind a cache.
     *
     * @param properties geocoding settings
     * @param clock      time source for the cache and the rate limit
     * @return the geocoder port
     */
    @Bean
    GeocoderPort geocoderPort(OrbitProperties properties, Clock clock) {
        OrbitProperties.Geocoding cfg = properties.geocoding();
        if ("stub".equalsIgnoreCase(cfg.provider())) {
            log.info("Geocoding provider: in-process stub (no external calls)");
            return new StubGeocoder();
        }
        log.info("Geocoding provider: Nominatim at {}", cfg.baseUrl());
        return new CachingGeocoder(new NominatimGeocoder(cfg, clock), clock, cfg.cacheTtl(), cfg.negativeCacheTtl(),
                cfg.cacheMaxEntries());
    }
}
