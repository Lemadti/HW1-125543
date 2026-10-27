package pt.ua.deti.tqs.orbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.orbit.adapter.geocoding.StubGeocoder;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.GeoPoint;
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.domain.Station;
import pt.ua.deti.tqs.orbit.port.GeocodingUnavailableException;
import pt.ua.deti.tqs.orbit.repository.CompartmentRepository;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;
import pt.ua.deti.tqs.orbit.repository.StationRepository;
import pt.ua.deti.tqs.orbit.service.view.NearbySearchResult;
import pt.ua.deti.tqs.orbit.service.view.SizeCounts;
import pt.ua.deti.tqs.orbit.service.view.StationSummary;
import pt.ua.deti.tqs.orbit.support.TestData;
import pt.ua.deti.tqs.orbit.support.TestProperties;

/**
 * Unit tests for the station search of {@link StationService}: distance ranking belongs to our code.
 *
 * <p>No Spring context: the geocoder is the in-process {@link StubGeocoder} (a fake that knows a few Aveiro
 * addresses and can be made unavailable) and the repositories are Mockito mocks.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StationService — ranking stations by distance (geocoder faked, repositories mocked)")
class StationRankingTest {

    private static final GeoPoint ORIGIN = new GeoPoint(40.6405, -8.6538);

    private final Station near = TestData.station(1, "NEAR", 40.6412, -8.6527);   // ~0.12 km
    private final Station mid = TestData.station(2, "MID", 40.6440, -8.6400);     // ~1.23 km
    private final Station far = TestData.station(3, "FAR", 40.6007, -8.6670);     // ~4.56 km
    private final Station out = TestData.station(4, "OUT", 38.7223, -9.1393);     // Lisboa

    @Mock
    private StationRepository stations;
    @Mock
    private CompartmentRepository compartments;
    @Mock
    private ParcelRepository parcels;

    private final StubGeocoder geocoder = new StubGeocoder();
    private StationService service;

    @BeforeEach
    void setUp() {
        service = new StationService(stations, compartments, parcels, geocoder, TestProperties.defaults());
        // every station has 16 compartments: a fake answer for the tests that build station summaries
        lenient().when(compartments.countByStation(any())).thenReturn(16L);
    }

    private Map<Long, SizeCounts> free(long nearM, long midM, long farM) {
        return Map.of(1L, new SizeCounts(1, nearM, 1), 2L, new SizeCounts(1, midM, 1), 3L, new SizeCounts(1, farM, 1),
                4L, new SizeCounts(1, 1, 1));
    }

    @Test
    @DisplayName("US1: stations are ranked by distance and those outside the radius are left out")
    void ranksByDistanceAndDropsStationsOutsideTheRadius() {
        List<StationSummary> ranked = service.rank(ORIGIN, List.of(far, out, mid, near), free(1, 1, 1), null, 5.0, 10);
        assertThat(ranked).extracting(StationSummary::code).containsExactly("NEAR", "MID", "FAR");
        assertThat(ranked.get(0).distanceKm()).isLessThan(ranked.get(1).distanceKm());
    }

    @Test
    @DisplayName("US1: only stations with a free compartment of the size are listed")
    void filtersBySizeAvailability() {
        List<StationSummary> ranked = service.rank(ORIGIN, List.of(far, mid, near), free(0, 2, 1), CompartmentSize.M, 5.0, 10);
        assertThat(ranked).extracting(StationSummary::code).containsExactly("MID", "FAR");
    }

    @Test
    @DisplayName("US1: the radius and the maximum number of results are respected")
    void honoursRadiusAndMaxResults() {
        assertThat(service.rank(ORIGIN, List.of(far, mid, near), free(1, 1, 1), null, 2.0, 10))
                .extracting(StationSummary::code).containsExactly("NEAR", "MID");
        assertThat(service.rank(ORIGIN, List.of(far, mid, near), free(1, 1, 1), null, 5.0, 1))
                .extracting(StationSummary::code).containsExactly("NEAR");
    }

    @Test
    @DisplayName("US1: the address is geocoded and stations are ranked from that point")
    void geocodesTheAddressAndRanksFromThatPoint() {
        when(stations.findAll()).thenReturn(List.of(far, near, mid));
        when(compartments.countAllocatableByStationAndSize()).thenReturn(List.<Object[]>of(
                new Object[]{1L, CompartmentSize.M, 2L}, new Object[]{2L, CompartmentSize.S, 3L}));

        NearbySearchResult result = service.nearbyAddress("Rua de Coimbra 10, Aveiro", null, null);

        assertThat(result.originLatitude()).isEqualTo(40.6405);
        assertThat(result.stations()).extracting(StationSummary::code).containsExactly("NEAR", "MID", "FAR");
        assertThat(result.stations().get(0).free()).isEqualTo(new SizeCounts(0, 2, 0));
        assertThat(result.radiusKm()).isNull();
    }

    @Test
    @DisplayName("US1: the radius chosen by the user limits the results")
    void userChosenRadiusLimitsTheResults() {
        when(stations.findAll()).thenReturn(List.of(far, near, mid));
        when(compartments.countAllocatableByStationAndSize()).thenReturn(List.of());

        NearbySearchResult result = service.nearbyAddress("Rua de Coimbra 10, Aveiro", null, 2.0);

        assertThat(result.radiusKm()).isEqualTo(2.0);
        assertThat(result.stations()).extracting(StationSummary::code).containsExactly("NEAR", "MID");
        assertThat(service.nearbyPoint(new GeoPoint(40.6405, -8.6538), null, 1.0).stations())
                .extracting(StationSummary::code).containsExactly("NEAR");
    }

    @ParameterizedTest(name = "radius {0} km is refused")
    @ValueSource(doubles = {0.0, -1.0, 20.5, 100.0, Double.NaN})
    @DisplayName("US1: a radius outside ]0, 20] km is refused")
    void radiusOutsideTheAllowedRangeIsRejected(double radius) {
        assertThatThrownBy(() -> service.nearbyAddress("Rua de Coimbra 10, Aveiro", null, radius))
                .isInstanceOfSatisfying(OrbitException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("INVALID_RADIUS");
                    assertThat(e.getDetails()).containsEntry("maxRadiusKm", 20.0);
                });
    }

    @ParameterizedTest(name = "radius {0} km is accepted")
    @ValueSource(doubles = {0.1, 1.0, 20.0})
    @DisplayName("US1: a radius inside ]0, 20] km is accepted")
    void radiusWithinTheAllowedRangeIsAccepted(double radius) {
        assertThatNoException().isThrownBy(() -> service.validateRadius(radius));
    }

    @Test
    @DisplayName("US1: an address the geocoder does not know is ADDRESS_NOT_FOUND")
    void unknownAddressIsReportedAsNotFound() {
        assertThatThrownBy(() -> service.nearbyAddress("Rua Inexistente 999", null, null))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("ADDRESS_NOT_FOUND"));
    }

    @Test
    @DisplayName("NFR4: a provider failure reaches the web layer, which falls back")
    void providerFailurePropagatesForTheFallback() {
        geocoder.setUnavailable(true);
        assertThatThrownBy(() -> service.nearbyAddress("Rua de Coimbra 10, Aveiro", null, null))
                .isInstanceOf(GeocodingUnavailableException.class);
    }

    @Test
    @DisplayName("US1: a blank address is refused")
    void blankAddressIsRejected() {
        assertThatThrownBy(() -> service.nearbyAddress("  ", null, null))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("ADDRESS_REQUIRED"));
    }

    @Test
    @DisplayName("NFR4: the fallback lists every station without distance")
    void fallbackListsAllStationsWithoutDistance() {
        when(stations.findAllByOrderByNameAsc()).thenReturn(List.of(far, mid));
        when(compartments.countAllocatableByStationAndSize()).thenReturn(List.of());
        assertThat(service.listAll(null)).allSatisfy(s -> assertThat(s.distanceKm()).isNull()).hasSize(2);
        assertThat(service.listAll(CompartmentSize.M)).isEmpty();
        when(stations.findByCode("X")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail("X")).isInstanceOf(OrbitException.class);
    }
}
