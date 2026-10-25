package pt.ua.deti.tqs.orbit.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * All configurable business rules (see application.yml, prefix {@code orbit}).
 */
@ConfigurationProperties(prefix = "orbit")
public record OrbitProperties(
        @DefaultValue Rules rules,
        @DefaultValue Codes codes,
        @DefaultValue Search search,
        @DefaultValue Geocoding geocoding,
        @DefaultValue Hardware hardware,
        @DefaultValue Demo demo,
        @DefaultValue("+48 22 100 20 30") String assistancePhone) {

    /** Allocation, lease and deadline rules. */
    public record Rules(
            @DefaultValue({"24", "48", "72"}) List<Integer> leaseHoursOptions,
            @DefaultValue("48") int defaultLeaseHours,
            @DefaultValue("12.00") BigDecimal pricePerDay,
            @DefaultValue("PLN") String currency,
            @DefaultValue("15d") Duration pickupWindow,
            @DefaultValue("7d") Duration maxLeaseStartAhead,
            @DefaultValue("5m") Duration terminalSessionTimeout) {
    }

    /** Drop-off / pickup PIN rules. */
    public record Codes(
            @DefaultValue("6") int pinLength,
            @DefaultValue("3") int maxFailedAttempts,
            @DefaultValue("1h") Duration lockout,
            @DefaultValue("change-me-in-production") String hmacSecret) {
    }

    /** Station search (UC1). */
    public record Search(
            @DefaultValue("5.0") double radiusKm,
            @DefaultValue("10") int maxResults) {
    }

    /** Geocoding provider (OpenStreetMap Nominatim, or an in-process stub for tests / load tests). */
    public record Geocoding(
            @DefaultValue("nominatim") String provider,
            @DefaultValue("https://nominatim.openstreetmap.org") String baseUrl,
            @DefaultValue("OrbitLockers-TQS-HW1/1.0 (university project)") String userAgent,
            @DefaultValue("pl") String countryCodes,
            @DefaultValue("2s") Duration connectTimeout,
            @DefaultValue("3s") Duration readTimeout,
            @DefaultValue("1s") Duration minInterval,
            @DefaultValue("24h") Duration cacheTtl,
            @DefaultValue("1h") Duration negativeCacheTtl,
            @DefaultValue("1000") int cacheMaxEntries) {
    }

    /** Fake locker hardware behaviour. */
    public record Hardware(
            /* In demo mode, the fake reports a door as closed as soon as it is read after being opened. */
            @DefaultValue("true") boolean autoClose) {
    }

    /** Demo helpers (inbox, fault injection, clock travel). Never enable in production. */
    public record Demo(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("false") boolean seedParcels) {
    }
}
