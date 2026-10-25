package pt.ua.deti.tqs.orbit.functional;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import pt.ua.deti.tqs.orbit.service.DeadlineService;

/** UC6 on the operator pages. */
class OperatorWebIT extends AbstractWebIT {

    @Autowired
    DeadlineService deadlines;

    @Test
    void operatorSeesExpiredParcelsAndRegistersTheirRemoval() {
        JsonNode reg = register(CENTRUM, "S", "S-05", 24);
        deliverToLocker(CENTRUM, reg);
        clock.advance(Duration.ofDays(15));
        deadlines.processDeadlines();
        String tc = reg.get("trackingCode").asString();

        open("/operator/stations/ORB-WAW-014");
        page.getByTestId("filter-EXPIRED").click();
        assertThat(page.getByTestId("parcel-row")).hasCount(1);
        page.getByTestId("remove-" + tc).click();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.ALERT)).containsText("RETURNED");

        open("/operator/parcels/" + tc);
        assertThat(page.getByTestId("state-badge")).hasText("Returned");
        assertThat(page.getByTestId("lc-RETURNED")).hasAttribute("aria-current", "step");
        assertThat(page.getByTestId("history")).containsText("OPERATOR");
    }

    @Test
    void operatorPutsAFaultyCompartmentOutOfService() {
        open("/operator/stations/ORB-WAW-014");
        assertThat(page.getByTestId("count-oos")).hasText("2");
        page.getByTestId("cell-M-15").click();
        page.getByTestId("oos-reason").fill("Door sensor faulty");
        page.getByTestId("out-of-service").click();

        assertThat(page.getByTestId("count-oos")).hasText("3");
        assertThat(page.getByTestId("incidents")).containsText("M-15");
    }
}
