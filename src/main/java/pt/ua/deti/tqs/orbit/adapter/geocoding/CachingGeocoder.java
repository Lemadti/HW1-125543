package pt.ua.deti.tqs.orbit.adapter.geocoding;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import pt.ua.deti.tqs.orbit.domain.GeoPoint;
import pt.ua.deti.tqs.orbit.port.GeocoderPort;

/**
 * Caches geocoding results (decorator). Found addresses are cached for {@code ttl}, unknown addresses for
 * {@code negativeTtl}; failures are never cached. Keys are normalised (trimmed, lower case, single spaces).
 */
public class CachingGeocoder implements GeocoderPort {

    private record Entry(Optional<GeoPoint> value, Instant expiresAt) {
    }

    private final GeocoderPort delegate;
    private final Clock clock;
    private final Duration ttl;
    private final Duration negativeTtl;
    private final int maxEntries;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public CachingGeocoder(GeocoderPort delegate, Clock clock, Duration ttl, Duration negativeTtl, int maxEntries) {
        this.delegate = delegate;
        this.clock = clock;
        this.ttl = ttl;
        this.negativeTtl = negativeTtl;
        this.maxEntries = maxEntries;
    }

    @Override
    public Optional<GeoPoint> geocode(String address) {
        String key = normalise(address);
        Instant now = clock.instant();
        Entry cached = cache.get(key);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.value();
        }
        Optional<GeoPoint> result = delegate.geocode(address);
        if (cache.size() >= maxEntries) {
            evictExpired(now);
        }
        if (cache.size() < maxEntries) {
            cache.put(key, new Entry(result, now.plus(result.isPresent() ? ttl : negativeTtl)));
        }
        return result;
    }

    public int size() {
        return cache.size();
    }

    static String normalise(String address) {
        return address.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private void evictExpired(Instant now) {
        Iterator<Map.Entry<String, Entry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            if (!now.isBefore(it.next().getValue().expiresAt())) {
                it.remove();
            }
        }
    }
}
