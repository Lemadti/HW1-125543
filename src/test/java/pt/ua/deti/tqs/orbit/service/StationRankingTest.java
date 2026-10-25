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

    private static final GeoPoint ORIGIN = new GeoPoint(52.2325, 21.0111);

    private final Station near = TestData.station(1, "NEAR", 52.2317, 21.0092);   // ~0.16 km
    private final Station mid = TestData.station(2, "MID", 52.2337, 21.0190);     // ~0.55 km
    private final Station far = TestData.station(3, "FAR", 52.2128, 21.0221);     // ~2.3 km
    private final Station out = TestData.station(4, "OUT", 52.4064, 16.9252);     // Poznań

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
        assertThat(service.rank(ORIGIN, List.of(far, mid, near), free(1, 1, 1), null, 1.0, 10))
                .extracting(StationSummary::code).containsExactly("NEAR", "MID");
        assertThat(service.rank(ORIGIN, List.of(far, mid, near), free(1, 1, 1), null, 5.0, 1))
                .extracting(StationSummary::code).containsExactly("NEAR");
    }

    @Test
    void geocodesTheAddressAndRanksFromThatPoint() {
        when(stations.findAll()).thenReturn(List.of(far, near, mid));
        when(compartments.countAllocatableByStationAndSize()).thenReturn(List.<Object[]>of(
                new Object[]{1L, CompartmentSize.M, 2L}, new Object[]{2L, CompartmentSize.S, 3L}));

        NearbySearchResult result = service.nearbyAddress("Chmielna 30, Warsaw", null);

        assertThat(result.originLatitude()).isEqualTo(52.2325);
        assertThat(result.stations()).extracting(StationSummary::code).containsExactly("NEAR", "MID", "FAR");
        assertThat(result.stations().get(0).free()).isEqualTo(new SizeCounts(0, 2, 0));
    }

    @Test
    void unknownAddressIsReportedAsNotFound() {
        assertThatThrownBy(() -> service.nearbyAddress("Nowhere 999", null))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("ADDRESS_NOT_FOUND"));
    }

    @Test
    void providerFailurePropagatesForTheFallback() {
        geocoder.setUnavailable(true);
        assertThatThrownBy(() -> service.nearbyAddress("Chmielna 30, Warsaw", null))
                .isInstanceOf(GeocodingUnavailableException.class);
    }

    @Test
    void blankAddressIsRejected() {
        assertThatThrownBy(() -> service.nearbyAddress("  ", null))
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
