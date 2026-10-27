package pt.ua.deti.tqs.orbit.functional;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.adapter.geocoding.StubGeocoder;

/** US1 — find a locker, through the browser. */
@DisplayName("Find stations (US1) — browser + real server + PostgreSQL")
class FindStationsWebIT extends AbstractWebIT {

    /** Front door: what a user does, with the locators a user would use (label, button name). */
    private void searchFor(String address) {
        page.getByLabel("Search near an address").fill(address);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Search").setExact(true)).click();
    }

    /** Exact text: "5 stations" must not be satisfied by "15 stations". */
    private Locator text(String text) {
        return page.getByText(text, new Page.GetByTextOptions().setExact(true));
    }

    /** The station cards of the result list. */
    private Locator stationCards() {
        return page.getByTestId("results").getByRole(AriaRole.BUTTON);
    }

    @Test
    @DisplayName("US1: searching an address lists the nearest stations; selecting one shows its free compartments")
    void searchingAnAddressListsTheNearestStationsWithFreeCompartments() {
        open("/");
        searchFor("Rua de Coimbra 10, Aveiro");

        assertThat(text("5 stations nearby")).isVisible();
        assertThat(stationCards().first()).containsText("Fórum Aveiro");
        assertThat(stationCards().first()).containsText("lockers available");

        stationCards().first().click();
        assertThat(page.getByTestId("station-detail")).containsText("ORB-AVR-001");
        assertThat(page.getByTestId("free-m")).hasText("4");
    }

    @Test
    @DisplayName("US1: before any search every station is listed, alphabetically")
    void allStationsAreListedBeforeAnySearch() {
        open("/");

        assertThat(text("5 stations")).isVisible();
        assertThat(stationCards()).hasCount(5);
        assertThat(page.getByText("Showing all stations")).isVisible();
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Show all stations"))).isHidden();
    }

    @Test
    @DisplayName("US1: the user chooses the search radius, and can go back to every station")
    void theUserChoosesTheSearchRadius() {
        open("/");
        searchFor("Rua de Coimbra 10, Aveiro");
        assertThat(text("5 stations nearby")).isVisible();

        page.getByLabel("Radius").selectOption("2");
        assertThat(text("4 stations within 2 km")).isVisible();

        page.getByLabel("Radius").selectOption("1");
        assertThat(text("1 station within 1 km")).isVisible();

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Show all stations")).click();
        assertThat(text("5 stations")).isVisible();
        assertThat(page.getByLabel("Search near an address")).hasValue("");
    }

    @Test
    @DisplayName("US1: an unknown address is reported to the user")
    void unknownAddressShowsAnError() {
        open("/");
        searchFor("Nowhere street 999");

        assertThat(page.getByRole(AriaRole.ALERT)).containsText("could not find");
    }

    @Test
    @DisplayName("NFR4: with the geocoder down the page falls back to every station")
    void geocoderDownFallsBackToAllStations() {
        ((StubGeocoder) geocoder).setUnavailable(true);
        open("/");
        searchFor("Rua de Coimbra 10, Aveiro");

        assertThat(page.getByText("temporarily unavailable")).isVisible();
        assertThat(stationCards()).hasCount(5);
    }
}
