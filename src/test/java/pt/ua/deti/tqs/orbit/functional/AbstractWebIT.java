package pt.ua.deti.tqs.orbit.functional;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import pt.ua.deti.tqs.orbit.support.AbstractIntegrationTest;

/**
 * Browser tests: the whole application on a random port, a real PostgreSQL, driven through a real browser (Playwright,
 * headless Chromium).
 *
 * <p>Lifecycle (as in Labs 05/06): one browser for the whole class (static), a new context and page for every test, so
 * nothing leaks between tests. Every test starts from a known state ({@link AbstractIntegrationTest#resetWorld()}).</p>
 *
 * <p>Front door / back door: what the user does and sees goes through the browser ({@code page}), with the locators a
 * user would use — role and accessible name, label, visible text. A {@code data-testid} is used only for elements that
 * have no role or name to find them by (counters, the state badge, the codes on the confirmation). Arranging data ("a
 * parcel waiting for pickup") and checking what was stored go through the back door (services and repositories).</p>
 *
 * <p>Stability: Playwright auto-waits and its web-first assertions retry, so there is no sleep; requests to external
 * hosts (map tiles, CDN, fonts) are blocked so the tests never depend on the network. For AssertJ, write
 * {@code Assertions.assertThat(...)}: the static {@code assertThat} in the subclasses is Playwright's.</p>
 */
@Tag("e2e")
abstract class AbstractWebIT extends AbstractIntegrationTest {

    static Playwright playwright;
    static Browser browser;
    BrowserContext context;
    Page page;

    @BeforeAll
    static void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        PlaywrightAssertions.setDefaultAssertionTimeout(7000);
    }

    @AfterAll
    static void close() {
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openPage() {
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 900).setLocale("en-GB"));
        context.route("**/*", route -> {
            String url = route.request().url();
            if (url.startsWith("http://localhost:" + port)) {
                route.resume();
            } else {
                route.abort();
            }
        });
        page = context.newPage();
        page.setDefaultTimeout(7000);
    }

    @AfterEach
    void closePage() {
        context.close();
    }

    String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** Navigates and waits for the DOM only; web-first assertions then wait for the data rendered by the scripts. */
    void open(String path) {
        page.navigate(url(path), new Page.NavigateOptions()
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(20000));
    }
}
