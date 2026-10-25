package pt.ua.deti.tqs.orbit.adapter.geocoding;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.domain.GeoPoint;
import pt.ua.deti.tqs.orbit.port.GeocoderPort;
import pt.ua.deti.tqs.orbit.port.GeocodingUnavailableException;
import pt.ua.deti.tqs.orbit.port.GeocodingUnavailableException.Reason;

/**
 * Geocoder adapter for OpenStreetMap Nominatim (https://nominatim.org/release-docs/latest/api/Search/).
 *
 * <p>Usage policy (https://operations.osmfoundation.org/policies/nominatim/): at most 1 request per second, an
 * identifying User-Agent, and results cached. This client enforces the minimum interval locally (a request that would
 * exceed it fails fast as RATE_LIMITED instead of queueing), sends a fixed User-Agent and uses short timeouts.
 * Caching is done by {@link CachingGeocoder}.</p>
 */
public class NominatimGeocoder implements GeocoderPort {

    private static final Logger log = LoggerFactory.getLogger("orbit.geocoding");

    record Place(String lat, String lon, String display_name) {
    }

    private final RestClient restClient;
    private final String countryCodes;
    private final Duration minInterval;
    private final Clock clock;
    private Instant lastCall = Instant.MIN;

    public NominatimGeocoder(OrbitProperties.Geocoding cfg, Clock clock) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(cfg.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(cfg.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(cfg.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", cfg.userAgent())
                .defaultHeader("Accept-Language", "en")
                .build();
        this.countryCodes = cfg.countryCodes();
        this.minInterval = cfg.minInterval();
        this.clock = clock;
    }

    @Override
    public Optional<GeoPoint> geocode(String address) {
        acquirePermit(address);
        long start = System.nanoTime();
        try {
            Place[] places = restClient.get()
                    .uri(b -> b.path("/search")
                            .queryParam("q", address)
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 1)
                            .queryParam("countrycodes", countryCodes)
                            .build())
                    .retrieve()
                    .body(Place[].class);
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (places == null || places.length == 0) {
                log.info("GEOCODE q=\"{}\" -> NOT_FOUND ({} ms)", address, ms);
                return Optional.empty();
            }
            GeoPoint point = new GeoPoint(Double.parseDouble(places[0].lat()), Double.parseDouble(places[0].lon()));
            log.info("GEOCODE q=\"{}\" -> {},{} ({} ms)", address, point.latitude(), point.longitude(), ms);
            return Optional.of(point);
        } catch (HttpStatusCodeException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.TOO_MANY_REQUESTS || status == HttpStatus.FORBIDDEN) {
                log.warn("GEOCODE q=\"{}\" -> RATE_LIMITED by provider (HTTP {})", address, e.getStatusCode().value());
                throw new GeocodingUnavailableException(Reason.RATE_LIMITED, "Geocoding provider usage limit reached", e);
            }
            log.warn("GEOCODE q=\"{}\" -> PROVIDER_ERROR (HTTP {})", address, e.getStatusCode().value());
            throw new GeocodingUnavailableException(Reason.PROVIDER_ERROR, "Geocoding provider error", e);
        } catch (ResourceAccessException e) {
            boolean timeout = hasCause(e, HttpTimeoutException.class);
            log.warn("GEOCODE q=\"{}\" -> {} ({})", address, timeout ? "TIMEOUT" : "UNREACHABLE", e.getMessage());
            throw new GeocodingUnavailableException(timeout ? Reason.TIMEOUT : Reason.PROVIDER_ERROR,
                    "Geocoding provider unavailable", e);
        } catch (RestClientException | NumberFormatException e) {
            log.warn("GEOCODE q=\"{}\" -> INVALID_RESPONSE ({})", address, e.getMessage());
            throw new GeocodingUnavailableException(Reason.PROVIDER_ERROR, "Invalid response from geocoding provider", e);
        }
    }

    private synchronized void acquirePermit(String address) {
        Instant now = clock.instant();
        if (lastCall != Instant.MIN && now.isBefore(lastCall.plus(minInterval))) {
            log.warn("GEOCODE q=\"{}\" -> RATE_LIMITED locally (min interval {})", address, minInterval);
            throw new GeocodingUnavailableException(Reason.RATE_LIMITED,
                    "Too many address searches, please retry in a moment");
        }
        lastCall = now;
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }
}
