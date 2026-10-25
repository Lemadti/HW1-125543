package pt.ua.deti.tqs.orbit.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * All configurable business rules (see application.yml, prefix {@code orbit}).
 *
 * @param rules           allocation, lease, price and deadline rules
 * @param codes           drop-off / pickup PIN rules
 * @param search          station search (UC1)
 * @param geocoding       geocoding provider settings
 * @param hardware        fake locker hardware behaviour
 * @param demo            demo helpers
 * @param assistancePhone phone number shown when the terminal or the locker fails
 */
@ConfigurationProperties(prefix = "orbit")
public record OrbitProperties(
        @DefaultValue Rules rules,
        @DefaultValue Codes codes,
        @DefaultValue Search search,
        @DefaultValue Geocoding geocoding,
        @DefaultValue Hardware hardware,
        @DefaultValue Demo demo,
        @DefaultValue("+351 234 100 200") String assistancePhone) {

    /**
     * Allocation, lease and deadline rules.
     *
     * @param leaseHoursOptions      allowed lease durations in hours (the lease end is the drop-off deadline)
     * @param defaultLeaseHours      lease duration used when the request omits it
     * @param pricePerDay            price per started day of lease, VAT included
     * @param currency               currency code
     * @param pickupWindow           time allowed for pickup after drop-off
     * @param maxLeaseStartAhead     how far in the future a lease may start
     * @param terminalSessionTimeout time to close the door after it opened
     */
    public record Rules(
            @DefaultValue({"24", "48", "72"}) List<Integer> leaseHoursOptions,
            @DefaultValue("48") int defaultLeaseHours,
            @DefaultValue("3.00") BigDecimal pricePerDay,
            @DefaultValue("EUR") String currency,
            @DefaultValue("15d") Duration pickupWindow,
            @DefaultValue("7d") Duration maxLeaseStartAhead,
            @DefaultValue("5m") Duration terminalSessionTimeout) {
    }

    /**
     * Drop-off and pickup PIN rules.
     *
     * @param pinLength         number of digits in a PIN
     * @param maxFailedAttempts consecutive wrong codes at a terminal before code entry is blocked
     * @param lockout           how long code entry stays blocked
     * @param hmacSecret        key used to store PINs as HMAC-SHA256
     */
    public record Codes(
            @DefaultValue("6") int pinLength,
            @DefaultValue("3") int maxFailedAttempts,
            @DefaultValue("1h") Duration lockout,
            @DefaultValue("change-me-in-production") String hmacSecret) {
    }

    /**
     * Station search (UC1). The radius is chosen by the user for each search; these values only bound it.
     *
     * @param radiusOptionsKm radius choices offered by the web pages (in addition to "any distance")
     * @param maxRadiusKm     largest radius a client may request
     * @param maxResults      maximum number of stations returned by an address search
     */
    public record Search(
            @DefaultValue({"1", "2", "5", "10", "20"}) List<Double> radiusOptionsKm,
            @DefaultValue("20.0") double maxRadiusKm,
            @DefaultValue("10") int maxResults) {
    }

    /**
     * Geocoding provider (OpenStreetMap Nominatim, or an in-process stub for tests and load tests).
     *
     * @param provider         {@code nominatim} or {@code stub}
     * @param baseUrl          Nominatim base URL
     * @param userAgent        identifying User-Agent required by the Nominatim usage policy
     * @param countryCodes     country filter for results ({@code pt})
     * @param connectTimeout   HTTP connect timeout
     * @param readTimeout      HTTP read timeout
     * @param minInterval      minimum interval between requests (usage policy: 1 per second)
     * @param cacheTtl         how long found addresses are cached
     * @param negativeCacheTtl how long unknown addresses are cached
     * @param cacheMaxEntries  maximum number of cached addresses
     */
    public record Geocoding(
            @DefaultValue("nominatim") String provider,
            @DefaultValue("https://nominatim.openstreetmap.org") String baseUrl,
            @DefaultValue("OrbitLockers-TQS-HW1/1.0 (university project)") String userAgent,
            @DefaultValue("pt") String countryCodes,
            @DefaultValue("2s") Duration connectTimeout,
            @DefaultValue("3s") Duration readTimeout,
            @DefaultValue("1s") Duration minInterval,
            @DefaultValue("24h") Duration cacheTtl,
            @DefaultValue("1h") Duration negativeCacheTtl,
            @DefaultValue("1000") int cacheMaxEntries) {
    }

    /**
     * Fake locker hardware behaviour.
     *
     * @param autoClose in demo mode, an opened door reads as closed on the next read (a working locker)
     */
    public record Hardware(
            /* In demo mode, the fake reports a door as closed as soon as it is read after being opened. */
            @DefaultValue("true") boolean autoClose) {
    }

    /**
     * Demo helpers (inbox, fault injection, clock travel). Never enable in production.
     *
     * @param enabled     exposes {@code /demo} and {@code /api/v1/demo/**}
     * @param seedParcels creates demo parcels at startup when the database has none
     */
    public record Demo(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("false") boolean seedParcels) {
    }
}
