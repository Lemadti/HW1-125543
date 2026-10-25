package pt.ua.deti.tqs.orbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pt.ua.deti.tqs.orbit.domain.GeoPoint;

class DistanceCalculatorTest {

    @Test
    void samePointIsZero() {
        GeoPoint p = new GeoPoint(52.23, 21.01);
        assertThat(DistanceCalculator.distanceKm(p, p)).isZero();
    }

    @Test
    void isSymmetric() {
        GeoPoint a = new GeoPoint(52.2297, 21.0122);
        GeoPoint b = new GeoPoint(50.0647, 19.9450);
        assertThat(DistanceCalculator.distanceKm(a, b)).isEqualTo(DistanceCalculator.distanceKm(b, a));
    }

    /** Oracle: reference great-circle distances (e.g. movable-type.co.uk calculator). */
    @ParameterizedTest
    @CsvSource({
            "52.2297, 21.0122, 50.0647, 19.9450, 252.0, 1.5",   // Warsaw - Kraków
            "52.2297, 21.0122, 52.4064, 16.9252, 278.8, 1.5",   // Warsaw - Poznań
            "0, 0, 0, 1, 111.2, 0.1",                          // 1 degree on the equator
            "52.23165, 21.00917, 52.23250, 21.01110, 0.157, 0.01"}) // Centrum station - Chmielna 30
    void matchesReferenceDistances(double lat1, double lon1, double lat2, double lon2, double km, double tolerance) {
        assertThat(DistanceCalculator.distanceKm(new GeoPoint(lat1, lon1), new GeoPoint(lat2, lon2)))
                .isCloseTo(km, within(tolerance));
    }

    @ParameterizedTest
    @CsvSource({"0.0, 1", "0.3, 4", "1.0, 12", "5.0, 60"})
    void walkingMinutesAtFiveKmPerHour(double km, int minutes) {
        assertThat(DistanceCalculator.walkingMinutes(km)).isEqualTo(minutes);
    }
}
