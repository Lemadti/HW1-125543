package pt.ua.deti.tqs.orbit.port;

import java.util.Optional;
import pt.ua.deti.tqs.orbit.domain.GeoPoint;

/** Port to the external geocoding provider. */
public interface GeocoderPort {

    /**
     * Converts a free-text address into coordinates.
     *
     * @param address free-text address, e.g. "Rua de Coimbra 10, Aveiro"
     * @return the coordinates, or empty if the provider does not know the address
     * @throws GeocodingUnavailableException if the provider is slow, down or over its usage limits
     */
    Optional<GeoPoint> geocode(String address);
}
