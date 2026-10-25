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
 * Browser tests (Playwright, headless Chromium) against the application on a random port.
 * <p>Stability: locators use data-testid / roles; Playwright auto-waits for elements and web-first assertions retry;
 * test data is arranged through the API before the journey; requests to external hosts (map tiles, CDN, fonts)
 * are blocked so tests are hermetic and never depend on the network.</p>
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
