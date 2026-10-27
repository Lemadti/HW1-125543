package pt.ua.deti.tqs.orbit.functional;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.adapter.hardware.FakeLockerHardware.Fault;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.port.CompartmentRef;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;

/**
 * US3 + US4 on the locker terminal page. The hardware is the fake of the running application in its demo behaviour
 * (an open door reads as closed once the user says so); failures are injected through the back door.
 */
@DisplayName("Locker terminal (US3, US4) — browser + real server + fake hardware")
class TerminalWebIT extends AbstractWebIT {

    /** The code field, by its accessible name (the kiosk's main region is named after the same heading text). */
    private Locator pinField(String name) {
        return page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(name));
    }

    private Locator button(String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
    }

    private Locator screenTitle() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1));
    }

    /** Front door: types the code in the field labelled with the screen's question and asks to open. */
    private void enterCode(String label, String code) {
        pinField(label).fill(code);
        button("Open compartment").click();
    }

    @Test
    @DisplayName("US3 + US4: the courier drops the parcel off and the recipient picks it up")
    void courierDropsOffAndRecipientPicksUp() {
        RegistrationResult reg = register(FORUM, "M", "M-07");
        collect(reg);

        open("/terminal/ORB-AVR-001");
        button("Drop off a parcel").click();
        enterCode("Enter the drop-off PIN", reg.dropOffCode());
        assertThat(screenTitle()).hasText("Compartment M-07 is open");
        button("I have closed the door").click();
        assertThat(screenTitle()).hasText("Parcel stored");
        Assertions.assertThat(stateOf(reg.trackingCode())).isEqualTo(ParcelState.READY_FOR_PICKUP);

        String pickupCode = pickupCode(reg.trackingCode());
        button("Finish").click();
        button("Collect a parcel").click();
        enterCode("Enter your pickup code", pickupCode);
        assertThat(screenTitle()).hasText("Compartment M-07 is open");
        button("I have closed the door").click();

        assertThat(screenTitle()).hasText("Enjoy your parcel!");
        Assertions.assertThat(stateOf(reg.trackingCode())).isEqualTo(ParcelState.PICKED_UP);
    }

    @Test
    @DisplayName("Codes: wrong codes report the attempts left and the third blocks the terminal")
    void wrongCodesAreReportedAndTheThirdBlocksTheTerminal() {
        open("/terminal/ORB-AVR-001");
        button("Collect a parcel").click();

        enterCode("Enter your pickup code", "000000");
        assertThat(page.getByRole(AriaRole.ALERT)).containsText("2 attempt(s) left");
        enterCode("Enter your pickup code", "000001");
        assertThat(page.getByRole(AriaRole.ALERT)).containsText("1 attempt(s) left");
        enterCode("Enter your pickup code", "000002");

        assertThat(screenTitle()).hasText("Code entry blocked");
    }

    @Test
    @DisplayName("Door doesn't open: the courier is offered another compartment of the same size")
    void doorFailureOffersAnotherCompartment() {
        RegistrationResult reg = register(FORUM, "M", "M-07");
        collect(reg);
        hardware.inject(new CompartmentRef(FORUM, "M-07"), Fault.DOOR_WONT_OPEN);

        open("/terminal/ORB-AVR-001");
        button("Drop off a parcel").click();
        enterCode("Enter the drop-off PIN", reg.dropOffCode());
        assertThat(screenTitle()).hasText("The compartment did not open");
        button("Use another compartment").click();

        assertThat(screenTitle()).containsText("New compartment: M-");
        Assertions.assertThat(compartmentOf(reg.trackingCode())).startsWith("M-").isNotEqualTo("M-07");
    }
}
