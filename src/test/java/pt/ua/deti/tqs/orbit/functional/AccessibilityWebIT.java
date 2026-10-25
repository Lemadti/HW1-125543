package pt.ua.deti.tqs.orbit.functional;

import com.microsoft.playwright.Page;

import static org.assertj.core.api.Assertions.assertThat;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.deque.html.axecore.results.Rule;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** NFR2: automated WCAG 2.2 AA checks (axe-core) on every page. Keyboard walkthrough is manual (see README). */
class AccessibilityWebIT extends AbstractWebIT {

    @ParameterizedTest
    @ValueSource(strings = {"/", "/register", "/register/ORB-AVR-001?size=M", "/register/ORB-AVR-001/M-03?size=M",
            "/tracking", "/operator", "/operator/stations/ORB-AVR-001", "/terminal", "/terminal/ORB-AVR-001",
            "/courier", "/demo"})
    void pageHasNoWcagAaViolations(String path) {
        open(path);
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(20000));

        assertNoViolations();
    }

    private void assertNoViolations() {
        AxeResults results = new AxeBuilder(page)
                .withTags(List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"))
                .analyze();

        List<Rule> violations = results.getViolations();
        assertThat(violations)
                .as(() -> violations.stream()
                        .map(v -> v.getId() + " (" + v.getImpact() + "): " + v.getNodes().stream()
                                .map(n -> n.getTarget() + " " + n.getFailureSummary()).limit(3).collect(Collectors.joining(", ")))
                        .collect(Collectors.joining("\n")))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tracking", "operator-parcel"})
    void detailPagesHaveNoWcagAaViolations(String kind) {
        String tc = register(FORUM, "M", null, 24).get("trackingCode").asString();
        open(kind.equals("tracking") ? "/tracking/" + tc : "/operator/parcels/" + tc);
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(20000));
        assertNoViolations();
    }
}
