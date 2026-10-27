package pt.ua.deti.tqs.orbit.functional;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.service.DeadlineService;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;

/** US6 on the operator pages. */
@DisplayName("Operator (US6) — browser + real server + PostgreSQL")
class OperatorWebIT extends AbstractWebIT {

    @Autowired
    private DeadlineService deadlines;

    private Locator button(String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    @Test
    @DisplayName("US6: the operator sees the expired parcels and registers their removal")
    void operatorSeesExpiredParcelsAndRegistersTheirRemoval() {
        // back door: a parcel that has been waiting for 15 days
        RegistrationResult reg = register(FORUM, "S", "S-05");
        deliverToLocker(FORUM, reg);
        clock.advance(Duration.ofDays(15));
        deadlines.processDeadlines();
        String tc = reg.trackingCode();

        open("/operator/stations/ORB-AVR-001");
        button("Expired").click();
        Locator row = page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(tc));
        assertThat(row).hasCount(1);
        row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Register removal")).click();
        assertThat(page.getByRole(AriaRole.ALERT)).containsText("RETURNED");
        Assertions.assertThat(stateOf(tc)).isEqualTo(ParcelState.RETURNED);

        open("/operator/parcels/" + tc);
        assertThat(page.getByTestId("state-badge")).hasText("Returned");
        assertThat(page.getByTestId("lc-RETURNED")).hasAttribute("aria-current", "step");
        assertThat(page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Transition history")))
                .containsText("OPERATOR");
    }

    @Test
    @DisplayName("US6: the operator puts a faulty compartment out of service")
    void operatorPutsAFaultyCompartmentOutOfService() {
        open("/operator/stations/ORB-AVR-001");
        assertThat(page.getByTestId("count-oos")).hasText("2");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("M-15, Medium")).click();
        page.getByLabel("Reason").fill("Door sensor faulty");
        button("Put out of service").click();

        assertThat(page.getByTestId("count-oos")).hasText("3");
        assertThat(page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Malfunction reports")))
                .containsText("M-15");
        Assertions.assertThat(jdbc.queryForObject("SELECT c.status FROM compartment c JOIN station s ON s.id = c.station_id "
                + "WHERE s.code = ? AND c.code = 'M-15'", String.class, FORUM)).isEqualTo("OUT_OF_SERVICE");
    }
}
