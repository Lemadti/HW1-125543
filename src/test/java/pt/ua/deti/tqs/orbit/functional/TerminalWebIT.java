package pt.ua.deti.tqs.orbit.functional;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import pt.ua.deti.tqs.orbit.adapter.hardware.FakeLockerHardware.Fault;
import pt.ua.deti.tqs.orbit.port.CompartmentRef;

/** UC3 + UC4 on the locker terminal page, with the fake hardware behaving as a working locker (demo config). */
class TerminalWebIT extends AbstractWebIT {

    private void enterPin(String pin) {
        page.getByTestId("pin-input").fill(pin);
        page.getByTestId("pin-submit").click();
    }

    @Test
    void courierDropsOffAndRecipientPicksUp() {
        JsonNode reg = register(FORUM, "M", "M-07", 24);
        collect(reg);

        open("/terminal/ORB-AVR-001");
        page.getByTestId("start-dropoff").click();
        enterPin(reg.get("dropOffCode").asString());
        assertThat(page.getByTestId("screen-title")).hasText("Compartment M-07 is open");
        page.getByTestId("door-closed").click();
        assertThat(page.getByTestId("screen-title")).hasText("Parcel stored");

        String pickup = pickupCode(reg.get("trackingCode").asString());
        page.getByTestId("finish").click();
        page.getByTestId("start-pickup").click();
        enterPin(pickup);
        assertThat(page.getByTestId("screen-title")).hasText("Compartment M-07 is open");
        page.getByTestId("door-closed").click();
        assertThat(page.getByTestId("screen-title")).hasText("Enjoy your parcel!");
        assertThat(state(reg.get("trackingCode").asString())).isEqualTo("PICKED_UP");
    }

    @Test
    void wrongCodesAreReportedAndTheThirdBlocksTheTerminal() {
        open("/terminal/ORB-AVR-001");
        page.getByTestId("start-pickup").click();
        enterPin("000000");
        assertThat(page.getByTestId("pin-error")).containsText("2 attempt(s) left");
        enterPin("000001");
        assertThat(page.getByTestId("pin-error")).containsText("1 attempt(s) left");
        enterPin("000002");
        assertThat(page.getByTestId("screen-title")).hasText("Code entry blocked");
    }

    @Test
    void doorFailureOffersAnotherCompartment() {
        JsonNode reg = register(FORUM, "M", "M-07", 24);
        collect(reg);
        hardware.inject(new CompartmentRef(FORUM, "M-07"), Fault.DOOR_WONT_OPEN);

        open("/terminal/ORB-AVR-001");
        page.getByTestId("start-dropoff").click();
        enterPin(reg.get("dropOffCode").asString());
        assertThat(page.getByTestId("screen-title")).hasText("The compartment did not open");
        page.getByTestId("request-replacement").click();
        assertThat(page.getByTestId("screen-title")).containsText("New compartment: M-");
    }
}
