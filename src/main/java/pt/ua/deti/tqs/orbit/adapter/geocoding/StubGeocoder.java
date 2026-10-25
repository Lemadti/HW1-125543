package pt.ua.deti.tqs.orbit.adapter.geocoding;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import pt.ua.deti.tqs.orbit.domain.GeoPoint;
import pt.ua.deti.tqs.orbit.port.GeocoderPort;
import pt.ua.deti.tqs.orbit.port.GeocodingUnavailableException;

/**
 * In-process geocoder with a fixed table of addresses in the Aveiro zone.
 *
 * <p>Used by the functional, acceptance and load tests ({@code orbit.geocoding.provider=stub}) so that no test calls
 * the real provider. It can be switched to "unavailable" to exercise the fallback behaviour of the station search.</p>
 */
public class StubGeocoder implements GeocoderPort {

    /** Centre of Aveiro (Praça da República), also the result for the bare query "aveiro". */
    public static final GeoPoint AVEIRO_CENTRE = new GeoPoint(40.6405, -8.6538);

    private final Map<String, GeoPoint> known = new ConcurrentHashMap<>(Map.of(
            "rua de coimbra 10, aveiro", AVEIRO_CENTRE,
            "avenida dr. lourenço peixinho 50, aveiro", new GeoPoint(40.6425, -8.6470),
            "universidade de aveiro", new GeoPoint(40.6305, -8.6590),
            "rua da pega, aveiro", new GeoPoint(40.6310, -8.6430),
            "ílhavo", new GeoPoint(40.6007, -8.6670),
            "aveiro", AVEIRO_CENTRE));
    private volatile boolean unavailable;

    /** Creates the stub with the fixed Aveiro address table, available. */
    public StubGeocoder() {
        // table initialised above
    }

    /**
     * Looks the normalised address up in the fixed table.
     *
     * @param address free-text address
     * @return the coordinates, or empty if the address is not in the table
     * @throws GeocodingUnavailableException if the stub was switched to unavailable
     */
    @Override
    public Optional<GeoPoint> geocode(String address) {
        if (unavailable) {
            throw new GeocodingUnavailableException(GeocodingUnavailableException.Reason.PROVIDER_ERROR,
                    "Stub geocoder set to unavailable");
        }
        return Optional.ofNullable(known.get(CachingGeocoder.normalise(address)));
    }

    /**
     * Adds (or replaces) an address in the table.
     *
     * @param address free-text address (normalised to lower case)
     * @param point   coordinates to return for it
     */
    public void register(String address, GeoPoint point) {
        known.put(address.trim().toLowerCase(Locale.ROOT), point);
    }

    /**
     * Simulates the provider being down.
     *
     * @param unavailable true to make every call fail with {@link GeocodingUnavailableException}
     */
    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }
}
