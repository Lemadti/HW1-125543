package pt.ua.deti.tqs.orbit.functional;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.adapter.geocoding.StubGeocoder;

/** UC1 through the browser. */
class FindStationsWebIT extends AbstractWebIT {

    @Test
    void searchingAnAddressListsTheNearestStationsWithFreeCompartments() {
        open("/");
        page.getByTestId("address-input").fill("Rua de Coimbra 10, Aveiro");
        page.getByTestId("search-button").click();

        assertThat(page.getByTestId("result-count")).hasText("5 stations nearby");
        assertThat(page.getByTestId("station-name").first()).hasText("Fórum Aveiro");
        assertThat(page.getByTestId("station-availability").first()).containsText("lockers available");

        page.getByTestId("station-card").first().click();
        assertThat(page.getByTestId("station-detail")).containsText("ORB-AVR-001");
        assertThat(page.getByTestId("free-m")).hasText("4");
    }

    @Test
    void unknownAddressShowsAnError() {
        open("/");
        page.getByTestId("address-input").fill("Nowhere street 999");
        page.getByTestId("search-button").click();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.ALERT)).containsText("could not find");
    }

    @Test
    void geocoderDownFallsBackToAllStations() {
        ((StubGeocoder) geocoder).setUnavailable(true);
        open("/");
        page.getByTestId("address-input").fill("Rua de Coimbra 10, Aveiro");
        page.getByTestId("search-button").click();

        assertThat(page.locator("[data-alert]")).containsText("temporarily unavailable");
        assertThat(page.getByTestId("station-card")).hasCount(5);
    }
}
