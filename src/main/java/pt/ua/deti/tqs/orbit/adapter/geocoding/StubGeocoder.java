package pt.ua.deti.tqs.orbit.adapter.geocoding;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import pt.ua.deti.tqs.orbit.domain.GeoPoint;
import pt.ua.deti.tqs.orbit.port.GeocoderPort;
import pt.ua.deti.tqs.orbit.port.GeocodingUnavailableException;

/**
 * In-process geocoder with a fixed table of Warsaw addresses. Used by the functional, acceptance and load tests
 * ({@code orbit.geocoding.provider=stub}) so that no test calls the real provider. Can be switched to "unavailable"
 * to exercise the fallback behaviour.
 */
public class StubGeocoder implements GeocoderPort {

    public static final GeoPoint WARSAW_CENTRE = new GeoPoint(52.2297, 21.0122);

    private final Map<String, GeoPoint> known = new ConcurrentHashMap<>(Map.of(
            "chmielna 30, warsaw", new GeoPoint(52.23250, 21.01110),
            "marszałkowska 104, warsaw", new GeoPoint(52.23165, 21.00917),
            "nowy świat 1, warsaw", new GeoPoint(52.23200, 21.01880),
            "puławska 10, warsaw", new GeoPoint(52.21150, 21.02200),
            "warsaw", WARSAW_CENTRE));
    private volatile boolean unavailable;

    @Override
    public Optional<GeoPoint> geocode(String address) {
        if (unavailable) {
            throw new GeocodingUnavailableException(GeocodingUnavailableException.Reason.PROVIDER_ERROR,
                    "Stub geocoder set to unavailable");
        }
        return Optional.ofNullable(known.get(CachingGeocoder.normalise(address)));
    }

    public void register(String address, GeoPoint point) {
        known.put(address.trim().toLowerCase(Locale.ROOT), point);
    }

    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }
}
