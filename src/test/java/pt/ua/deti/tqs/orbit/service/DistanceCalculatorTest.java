package pt.ua.deti.tqs.orbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.ua.deti.tqs.orbit.domain.GeoPoint;

@DisplayName("DistanceCalculator — distance between two points")
class DistanceCalculatorTest {

    @Test
    @DisplayName("the distance from a point to itself is zero")
    void samePointIsZero() {
        GeoPoint p = new GeoPoint(40.64, -8.65);
        assertThat(DistanceCalculator.distanceKm(p, p)).isZero();
    }

    @Test
    @DisplayName("the distance A→B is the same as B→A")
    void isSymmetric() {
        GeoPoint a = new GeoPoint(40.6405, -8.6538);
        GeoPoint b = new GeoPoint(41.1579, -8.6291);
        assertThat(DistanceCalculator.distanceKm(a, b)).isEqualTo(DistanceCalculator.distanceKm(b, a));
    }

    /** Oracle: geodesic distances computed independently with Vincenty's formula on the WGS-84 ellipsoid. */
    @ParameterizedTest(name = "({0}, {1}) → ({2}, {3}) ≈ {4} km")
    @CsvSource({
            "40.6405, -8.6538, 41.1579, -8.6291, 57.50, 0.4",   // Aveiro - Porto
            "40.6405, -8.6538, 38.7223, -9.1393, 217.0, 1.2",   // Aveiro - Lisboa
            "0, 0, 0, 1, 111.2, 0.1",                          // 1 degree on the equator
            "52.23165, 21.00917, 52.23250, 21.01110, 0.157, 0.01"}) // Fórum Aveiro station - Chmielna 30
    @DisplayName("US1: distances match independently computed reference values")
    void matchesReferenceDistances(double lat1, double lon1, double lat2, double lon2, double km, double tolerance) {
        assertThat(DistanceCalculator.distanceKm(new GeoPoint(lat1, lon1), new GeoPoint(lat2, lon2)))
                .isCloseTo(km, within(tolerance));
    }

    @ParameterizedTest(name = "{0} km → {1} min")
    @CsvSource({"0.0, 1", "0.3, 4", "1.0, 12", "5.0, 60"})
    @DisplayName("US1: walking time is estimated at 5 km/h, rounded up")
    void walkingMinutesAtFiveKmPerHour(double km, int minutes) {
        assertThat(DistanceCalculator.walkingMinutes(km)).isEqualTo(minutes);
    }
}
