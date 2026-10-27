package pt.ua.deti.tqs.orbit.functional;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;

/** US2 + US5 on the web pages: the 3-step registration (as in the mockups), then tracking. */
@DisplayName("Register (US2) and track (US5) — browser + real server + PostgreSQL")
class RegisterAndTrackWebIT extends AbstractWebIT {

    private Locator button(String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
    }

    private Locator link(String name) {
        return page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    /** A labelled field inside a fieldset ("Sender contact", "Recipient contact"): the labels repeat in both. */
    private Locator field(String group, String label) {
        return page.getByRole(AriaRole.GROUP, new Page.GetByRoleOptions().setName(group)).getByLabel(label);
    }

    @Test
    @DisplayName("US2 + US5: a partner registers a medium parcel in three steps, then tracks it")
    void partnerRegistersAMediumParcelAndTracksIt() {
        // step 1: size and station
        open("/register");
        page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName("Medium")).click();
        page.getByLabel("Where do you need a locker?").fill("Rua de Coimbra 10, Aveiro");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Search").setExact(true)).click();
        // every station is listed alphabetically on load; wait until the search has ranked them by distance
        assertThat(page.getByText("5 matching stations nearby", new Page.GetByTextOptions().setExact(true))).isVisible();
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Fórum Aveiro")).click();

        // step 2: choose a compartment in the front view
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Fórum Aveiro"))).isVisible();
        button("M-11, Medium").click();
        assertThat(page.getByText("M-11 · Available to register")).isVisible();
        link("Register").click();

        // step 3: contacts (fixed drop-off window and price)
        assertThat(button("Submit registration")).isDisabled();
        assertThat(page.getByText("10 working days from now")).isVisible();
        field("Sender contact", "Full name").fill("Ana Silva");
        field("Sender contact", "Email address").fill("ana.silva@example.com");
        field("Sender contact", "Phone number").fill("+351 912 345 678");
        field("Recipient contact", "Full name").fill("João Santos");
        field("Recipient contact", "Email address").fill("joao.santos@example.com");
        field("Recipient contact", "Phone number").fill("+351 934 567 890");
        assertThat(page.getByText("3.00 EUR", new Page.GetByTextOptions().setExact(true))).isVisible();
        page.getByLabel("I agree to the locker reservation terms").check();
        button("Submit registration").click();

        // the user sees the confirmation and the codes...
        assertThat(page.getByText("Compartment M-11 reserved at Fórum Aveiro")).isVisible();
        assertThat(page.getByTestId("drop-off-code")).hasText(Pattern.compile("\\d{6}"));
        String trackingCode = page.getByTestId("tracking-code").textContent();
        Assertions.assertThat(trackingCode).matches("ORB-\\d{6}-\\d{4}");
        // ...and the back door shows what was stored
        Assertions.assertThat(stateOf(trackingCode)).isEqualTo(ParcelState.CREATED);
        Assertions.assertThat(compartmentOf(trackingCode)).isEqualTo("M-11");

        // US5: tracking
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Track parcel")).click();
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
                .hasText("Your parcel is registered.");
        assertThat(page.getByText("M-11 · Medium")).isVisible();
        assertThat(page.getByText("Registration created")).isVisible();
    }

    @Test
    @DisplayName("Compartment allocation: a reserved compartment cannot be chosen")
    void reservedCompartmentCannotBeChosen() {
        register(FORUM, "M", "M-07");

        open("/register/ORB-AVR-001?size=M");
        button("M-07, Medium").click();

        assertThat(page.getByText("M-07 · Reserved")).isVisible();
        assertThat(link("Register")).hasCount(0);
    }

    @Test
    @DisplayName("US2: an invalid email is reported next to the field")
    void invalidEmailIsReportedNextToTheField() {
        open("/register/ORB-AVR-001/M-03?size=M");
        field("Sender contact", "Email address").fill("not-an-email");
        field("Sender contact", "Full name").focus();

        assertThat(page.getByText("Please enter a valid email address.")).isVisible();
        assertThat(field("Sender contact", "Email address")).hasAttribute("aria-invalid", "true");
    }

    @Test
    @DisplayName("US5: an unknown tracking code is reported to the user")
    void unknownTrackingCodeShowsAnError() {
        open("/tracking");
        page.getByLabel("Tracking code").fill("ORB-261024-071X");
        button("Track parcel").click();

        assertThat(page.getByRole(AriaRole.ALERT)).containsText("Invalid tracking code");
    }

    @Test
    @DisplayName("US5: the recipient can cancel before the courier collects the parcel")
    void recipientCanCancelBeforeCollection() {
        RegistrationResult reg = register(FORUM, "S", null);
        open("/tracking");
        page.getByLabel("Tracking code").fill(reg.trackingCode());
        button("Track parcel").click();
        assertThat(page.getByTestId("state-badge")).hasText("Created");

        page.onDialog(d -> d.accept());
        button("Cancel this parcel").click();

        assertThat(page.getByTestId("state-badge")).hasText("Cancelled");
        Assertions.assertThat(stateOf(reg.trackingCode())).isEqualTo(ParcelState.CANCELLED);
        Assertions.assertThat(lastActorOf(reg.trackingCode())).isEqualTo("RECIPIENT");
    }
}
