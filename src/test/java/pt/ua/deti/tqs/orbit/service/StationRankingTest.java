package pt.ua.deti.tqs.orbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/** Distance ranking belongs to our code; the geocoder is replaced by the in-process stub (a fake). */
class StationRankingTest {

    private static final GeoPoint ORIGIN = new GeoPoint(40.6405, -8.6538);

    private final Station near = TestData.station(1, "NEAR", 40.6412, -8.6527);   // ~0.12 km
    private final Station mid = TestData.station(2, "MID", 40.6440, -8.6400);     // ~1.23 km
    private final Station far = TestData.station(3, "FAR", 40.6007, -8.6670);     // ~4.56 km
    private final Station out = TestData.station(4, "OUT", 38.7223, -9.1393);     // Lisboa

    private final StationRepository stations = mock(StationRepository.class);
    private final CompartmentRepository compartments = mock(CompartmentRepository.class);
    private final StubGeocoder geocoder = new StubGeocoder();
    private StationService service;

    @BeforeEach
    void setUp() {
        service = new StationService(stations, compartments, mock(ParcelRepository.class), geocoder,
                TestProperties.defaults());
        when(compartments.countByStation(any())).thenReturn(16L);
    }

    private Map<Long, SizeCounts> free(long nearM, long midM, long farM) {
        return Map.of(1L, new SizeCounts(1, nearM, 1), 2L, new SizeCounts(1, midM, 1), 3L, new SizeCounts(1, farM, 1),
                4L, new SizeCounts(1, 1, 1));
    }

    @Test
    void ranksByDistanceAndDropsStationsOutsideTheRadius() {
        List<StationSummary> ranked = service.rank(ORIGIN, List.of(far, out, mid, near), free(1, 1, 1), null, 5.0, 10);
        assertThat(ranked).extracting(StationSummary::code).containsExactly("NEAR", "MID", "FAR");
        assertThat(ranked.get(0).distanceKm()).isLessThan(ranked.get(1).distanceKm());
    }

    @Test
    void filtersBySizeAvailability() {
        List<StationSummary> ranked = service.rank(ORIGIN, List.of(far, mid, near), free(0, 2, 1), CompartmentSize.M, 5.0, 10);
        assertThat(ranked).extracting(StationSummary::code).containsExactly("MID", "FAR");
    }

    @Test
    void honoursRadiusAndMaxResults() {
        assertThat(service.rank(ORIGIN, List.of(far, mid, near), free(1, 1, 1), null, 2.0, 10))
                .extracting(StationSummary::code).containsExactly("NEAR", "MID");
        assertThat(service.rank(ORIGIN, List.of(far, mid, near), free(1, 1, 1), null, 5.0, 1))
                .extracting(StationSummary::code).containsExactly("NEAR");
    }

    @Test
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
    void userChosenRadiusLimitsTheResults() {
        when(stations.findAll()).thenReturn(List.of(far, near, mid));
        when(compartments.countAllocatableByStationAndSize()).thenReturn(List.of());

        NearbySearchResult result = service.nearbyAddress("Rua de Coimbra 10, Aveiro", null, 2.0);

        assertThat(result.radiusKm()).isEqualTo(2.0);
        assertThat(result.stations()).extracting(StationSummary::code).containsExactly("NEAR", "MID");
        assertThat(service.nearbyPoint(new GeoPoint(40.6405, -8.6538), null, 1.0).stations())
                .extracting(StationSummary::code).containsExactly("NEAR");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(doubles = {0.0, -1.0, 20.5, 100.0, Double.NaN})
    void radiusOutsideTheAllowedRangeIsRejected(double radius) {
        assertThatThrownBy(() -> service.nearbyAddress("Rua de Coimbra 10, Aveiro", null, radius))
                .isInstanceOfSatisfying(OrbitException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("INVALID_RADIUS");
                    assertThat(e.getDetails()).containsEntry("maxRadiusKm", 20.0);
                });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(doubles = {0.1, 1.0, 20.0})
    void radiusWithinTheAllowedRangeIsAccepted(double radius) {
        org.assertj.core.api.Assertions.assertThatNoException().isThrownBy(() -> service.validateRadius(radius));
    }

    @Test
    void unknownAddressIsReportedAsNotFound() {
        assertThatThrownBy(() -> service.nearbyAddress("Rua Inexistente 999", null, null))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("ADDRESS_NOT_FOUND"));
    }

    @Test
    void providerFailurePropagatesForTheFallback() {
        geocoder.setUnavailable(true);
        assertThatThrownBy(() -> service.nearbyAddress("Rua de Coimbra 10, Aveiro", null, null))
                .isInstanceOf(GeocodingUnavailableException.class);
    }

    @Test
    void blankAddressIsRejected() {
        assertThatThrownBy(() -> service.nearbyAddress("  ", null, null))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("ADDRESS_REQUIRED"));
    }

    @Test
    void fallbackListsAllStationsWithoutDistance() {
        when(stations.findAllByOrderByNameAsc()).thenReturn(List.of(far, mid));
        when(compartments.countAllocatableByStationAndSize()).thenReturn(List.of());
        assertThat(service.listAll(null)).allSatisfy(s -> assertThat(s.distanceKm()).isNull()).hasSize(2);
        assertThat(service.listAll(CompartmentSize.M)).isEmpty();
        when(stations.findByCode("X")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail("X")).isInstanceOf(OrbitException.class);
    }
}
