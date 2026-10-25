package pt.ua.deti.tqs.orbit.functional;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.Page;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** UC2 + UC5 on the web pages: the 3-step registration (as in the mockups), then tracking. */
class RegisterAndTrackWebIT extends AbstractWebIT {

    @Test
    void partnerRegistersAMediumParcelAndTracksIt() {
        open("/register");
        page.getByTestId("size-M").click();
        page.getByTestId("address-input").fill("Rua de Coimbra 10, Aveiro");
        page.getByTestId("search-button").click();
        // all stations are listed alphabetically on load; wait until the search has ranked them by distance
        assertThat(page.getByTestId("result-count")).hasText("5 matching stations nearby");
        page.getByTestId("station-card").first().click();

        // step 2: choose a compartment in the front view
        assertThat(page.getByTestId("station-title")).hasText("Fórum Aveiro");
        page.getByTestId("cell-M-11").click();
        assertThat(page.getByTestId("selection")).containsText("M-11 · Available to register");
        page.getByTestId("continue").click();

        // step 3: lease and contacts
        assertThat(page.getByTestId("submit")).isDisabled();
        page.getByTestId("duration-72").click();
        page.getByTestId("sender-name").fill("Ana Silva");
        page.getByTestId("sender-email").fill("ana.silva@example.com");
        page.getByTestId("sender-phone").fill("+351 912 345 678");
        page.getByTestId("recipient-name").fill("João Santos");
        page.getByTestId("recipient-email").fill("joao.santos@example.com");
        page.getByTestId("recipient-phone").fill("+351 934 567 890");
        assertThat(page.getByTestId("price")).hasText("9.00 EUR");
        page.getByTestId("terms").check();
        page.getByTestId("submit").click();

        assertThat(page.getByTestId("confirmation")).containsText("Compartment M-11 reserved at Fórum Aveiro");
        assertThat(page.getByTestId("drop-off-code")).hasText(Pattern.compile("\\d{6}"));
        String trackingCode = page.getByTestId("tracking-code").textContent();
        assertThat(trackingCode).matches("ORB-\\d{6}-\\d{4}");

        page.getByTestId("track-link").click();
        assertThat(page.getByTestId("tracking-title")).hasText("Your parcel is registered.");
        assertThat(page.getByTestId("compartment")).hasText("M-11 · Medium");
        assertThat(page.getByTestId("timeline")).containsText("Registration created");
    }

    @Test
    void reservedCompartmentCannotBeChosen() {
        register(FORUM, "M", "M-07", 24);
        open("/register/ORB-AVR-001?size=M");
        page.getByTestId("cell-M-07").click();
        assertThat(page.getByTestId("selection")).containsText("Reserved");
        assertThat(page.getByTestId("continue")).hasCount(0);
    }

    @Test
    void invalidEmailIsReportedNextToTheField() {
        open("/register/ORB-AVR-001/M-03?size=M");
        page.getByTestId("sender-email").fill("not-an-email");
        page.getByTestId("sender-name").focus();
        assertThat(page.locator("#s-email-err")).hasText("Please enter a valid email address.");
        assertThat(page.getByTestId("sender-email")).hasAttribute("aria-invalid", "true");
    }

    @Test
    void unknownTrackingCodeShowsAnError() {
        open("/tracking");
        page.getByTestId("tracking-input").fill("ORB-261024-071X");
        page.getByTestId("track-button").click();
        assertThat(page.getByTestId("tracking-error")).containsText("Invalid tracking code");
    }

    @Test
    void recipientCanCancelBeforeCollection() {
        String tc = register(FORUM, "S", null, 24).get("trackingCode").asString();
        open("/tracking");
        page.getByTestId("tracking-input").fill(tc);
        page.getByTestId("track-button").click();
        assertThat(page.getByTestId("state-badge")).hasText("Created");

        page.onDialog(d -> d.accept());
        page.getByTestId("cancel-parcel").click();
        assertThat(page.getByTestId("state-badge")).hasText("Cancelled");
    }
}
