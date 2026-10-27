package pt.ua.deti.tqs.orbit.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.orbit.adapter.geocoding.CachingGeocoder;
import pt.ua.deti.tqs.orbit.config.MutableClock;
import pt.ua.deti.tqs.orbit.domain.GeoPoint;
import pt.ua.deti.tqs.orbit.port.GeocoderPort;
import pt.ua.deti.tqs.orbit.port.GeocodingUnavailableException;
import pt.ua.deti.tqs.orbit.support.TestData;

/**
 * Unit tests for {@link CachingGeocoder}. A Mockito mock replaces the real provider on purpose: the oracle is the
 * number of calls that reach the provider ({@code verify(provider, times(n))}), which a fake could not show as
 * directly. Time is a {@link MutableClock}, so the TTLs are tested without waiting.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CachingGeocoder — NFR4 caching of geocoding results")
class CachingGeocoderTest {

    private final MutableClock clock = MutableClock.fixedAt(TestData.T0);
    @Mock
    private GeocoderPort provider;
    private CachingGeocoder cache;
    private final GeoPoint p = new GeoPoint(40.64, -8.65);

    @BeforeEach
    void setUp() {
        cache = new CachingGeocoder(provider, clock, Duration.ofHours(24), Duration.ofHours(1), 2);
    }

    @Test
    @DisplayName("NFR4: the same address (normalised) reaches the provider once per TTL")
    void cachesNormalisedAddressesUntilTtl() {
        when(provider.geocode("Rua de Coimbra 10, Aveiro")).thenReturn(Optional.of(p));

        assertThat(cache.geocode("Rua de Coimbra 10, Aveiro")).contains(p);
        assertThat(cache.geocode("  rua de   coimbra 10, AVEIRO ")).contains(p);
        verify(provider, times(1)).geocode("Rua de Coimbra 10, Aveiro");

        clock.advance(Duration.ofHours(24));
        cache.geocode("Rua de Coimbra 10, Aveiro");
        verify(provider, times(2)).geocode("Rua de Coimbra 10, Aveiro");
    }

    @Test
    @DisplayName("NFR4: 'not found' answers are cached for a shorter time")
    void notFoundIsCachedForAShorterTime() {
        when(provider.geocode("x")).thenReturn(Optional.empty());
        cache.geocode("x");
        cache.geocode("x");
        verify(provider, times(1)).geocode("x");
        clock.advance(Duration.ofHours(1));
        cache.geocode("x");
        verify(provider, times(2)).geocode("x");
    }

    @Test
    @DisplayName("NFR4: provider failures are not cached")
    void failuresAreNotCached() {
        when(provider.geocode("y"))
                .thenThrow(new GeocodingUnavailableException(GeocodingUnavailableException.Reason.TIMEOUT, "slow"))
                .thenReturn(Optional.of(p));
        assertThatThrownBy(() -> cache.geocode("y")).isInstanceOf(GeocodingUnavailableException.class);
        assertThat(cache.geocode("y")).contains(p);
    }

    @Test
    @DisplayName("the cache never grows beyond its maximum size")
    void boundedSize() {
        when(provider.geocode(anyString())).thenReturn(Optional.of(p));
        cache.geocode("a");
        cache.geocode("b");
        cache.geocode("c");
        assertThat(cache.size()).isEqualTo(2);
    }
}
