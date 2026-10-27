package pt.ua.deti.tqs.orbit.functional;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.Rule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.LoadState;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.util.ReflectionTestUtils;
import pt.ua.deti.tqs.orbit.adapter.geocoding.CachingGeocoder;
import pt.ua.deti.tqs.orbit.adapter.geocoding.NominatimGeocoder;
import pt.ua.deti.tqs.orbit.adapter.geocoding.StubGeocoder;
import pt.ua.deti.tqs.orbit.adapter.hardware.FakeLockerHardware;
import pt.ua.deti.tqs.orbit.config.MutableClock;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.domain.GeoPoint;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.port.CompartmentRef;
import pt.ua.deti.tqs.orbit.port.GeocodingUnavailableException;
import pt.ua.deti.tqs.orbit.service.CodeService;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;

/**
 * Non-functional requirements of the report (section 2.4), each verified against the running application.
 *
 * <ul>
 *   <li>NFR1 and NFR5 (response time with 50 concurrent users) are load requirements: they are verified by the k6
 *       script {@code load-tests/nfr-load.js}, not here.</li>
 *   <li>NFR2 accessibility, NFR3 pickup code security, NFR4 OpenStreetMap usage policy, NFR6 single-use codes,
 *       NFR7 client refresh and NFR8 lockout are verified below.</li>
 * </ul>
 *
 * <p>The real Nominatim server is never called: the application runs with the stub geocoder, and the Nominatim client
 * itself is pointed at a WireMock server.</p>
 */
class NonFunctionalRequirementsWebIT extends AbstractWebIT {

    /** MockMvc with AssertJ assertions, for the API checks of NFR3, NFR4, NFR6 and NFR8. */
    @Autowired
    private MockMvcTester tester;
    @Autowired
    private CodeService codes;
    @Autowired
    private OrbitProperties properties;

    private WireMockServer nominatimServer;

    @BeforeEach
    void startNominatimStub() {
        nominatimServer = new WireMockServer(wireMockConfig().dynamicPort());
        nominatimServer.start();
        nominatimServer.stubFor(WireMock.get(urlPathEqualTo("/search")).willReturn(okJson(
                "[{\"lat\":\"40.6405\",\"lon\":\"-8.6538\",\"display_name\":\"Rua de Coimbra, Aveiro\"}]")));
    }

    @AfterEach
    void stopNominatimStub() {
        nominatimServer.stop();
    }

    // =================================================================== NFR2: WCAG 2.2 Level AA

    /** The axe-core rule tags that together make up WCAG 2.2 Level AA (levels A and AA of 2.0, 2.1 and 2.2). */
    private static final List<String> WCAG_22_AA = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");

    /** Runs axe-core on the page as it is now; {@code state} names the screen in the failure message. */
    private void assertNoWcagViolations(String state) {
        List<Rule> violations = new AxeBuilder(page).withTags(WCAG_22_AA).analyze().getViolations();
        assertThat(violations)
                .as(() -> state + ": " + violations.stream()
                        .map(v -> v.getId() + " (" + v.getImpact() + ") on " + v.getNodes().stream()
                                .map(n -> String.valueOf(n.getTarget())).limit(3).collect(Collectors.joining(", ")))
                        .collect(Collectors.joining("; ")))
                .isEmpty();
    }

    private void enterPin(String pin) {
        page.getByTestId("pin-input").fill(pin);
        page.getByTestId("pin-submit").click();
    }

    @ParameterizedTest(name = "NFR2 {0}")
    @ValueSource(strings = {"/", "/register", "/register/ORB-AVR-001?size=M", "/register/ORB-AVR-001/M-03?size=M",
            "/tracking", "/operator", "/operator/stations/ORB-AVR-001", "/terminal", "/terminal/ORB-AVR-001",
            "/courier", "/demo", "TRACKING_DETAIL", "OPERATOR_PARCEL"})
    @DisplayName("NFR2: every page (the REST API excluded) has no WCAG 2.2 A/AA violation found by axe-core")
    void nfr2_everyPageMeetsWcag22LevelAa(String path) {
        if (path.equals("TRACKING_DETAIL") || path.equals("OPERATOR_PARCEL")) {
            String tc = register(FORUM, "M", null).trackingCode();
            path = path.equals("TRACKING_DETAIL") ? "/tracking/" + tc : "/operator/parcels/" + tc;
        }
        open(path);
        page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(20000));
        // the page really rendered (not an empty or error document) before it is scanned
        PlaywrightAssertions.assertThat(page.locator("main")).isVisible();

        assertNoWcagViolations(path);
    }

    @ParameterizedTest(name = "NFR2 map on {0}")
    @ValueSource(strings = {"/", "/register", "/register/ORB-AVR-001?size=M", "/operator", "TRACKING_DETAIL",
            "OPERATOR_PARCEL"})
    @DisplayName("NFR2: pages with the map (real Leaflet, served from the test resources) have no WCAG 2.2 AA violation")
    void nfr2_pagesWithTheMapMeetWcag22LevelAa(String path) throws Exception {
        if (path.equals("TRACKING_DETAIL") || path.equals("OPERATOR_PARCEL")) {
            String tc = register(FORUM, "M", null).trackingCode();
            path = path.equals("TRACKING_DETAIL") ? "/tracking/" + tc : "/operator/parcels/" + tc;
        }
        // the layout loads Leaflet 1.9.4 from a CDN; the same files are served locally so the test needs no network
        // (the map tiles stay blocked, but the map's own controls, markers and attribution are rendered)
        byte[] js = new ClassPathResource("leaflet/leaflet.min.js").getContentAsByteArray();
        byte[] css = new ClassPathResource("leaflet/leaflet.min.css").getContentAsByteArray();
        page.route("**/leaflet.min.js", r -> r.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                .setContentType("application/javascript").setBodyBytes(js)));
        page.route("**/leaflet.min.css", r -> r.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                .setContentType("text/css").setBodyBytes(css)));

        open(path);
        PlaywrightAssertions.assertThat(page.locator(".leaflet-control-attribution")).containsText("OpenStreetMap");
        page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(20000));

        assertNoWcagViolations("map on " + path);
    }

    @Test
    @DisplayName("NFR2: find stations, in each state (results, station detail, address not found, geocoder down)")
    void nfr2_findStationsStatesMeetWcag22LevelAa() {
        open("/");
        page.getByTestId("address-input").fill("Rua de Coimbra 10, Aveiro");
        page.getByTestId("search-button").click();
        PlaywrightAssertions.assertThat(page.getByTestId("result-count")).hasText("5 stations nearby");
        assertNoWcagViolations("search results");

        page.getByTestId("station-card").first().click();
        PlaywrightAssertions.assertThat(page.getByTestId("free-m")).isVisible();
        assertNoWcagViolations("station detail");

        page.getByTestId("address-input").fill("Nowhere street 999");
        page.getByTestId("search-button").click();
        PlaywrightAssertions.assertThat(page.locator("[data-alert]")).isVisible();
        assertNoWcagViolations("address not found");

        ((StubGeocoder) geocoder).setUnavailable(true);
        page.getByTestId("address-input").fill("Rua de Coimbra 10, Aveiro");
        page.getByTestId("search-button").click();
        PlaywrightAssertions.assertThat(page.locator("[data-alert]")).containsText("temporarily unavailable");
        assertNoWcagViolations("geocoder unavailable");
    }

    @Test
    @DisplayName("NFR2: registration, in each state (compartment selected, validation errors, confirmation)")
    void nfr2_registrationStatesMeetWcag22LevelAa() {
        register(FORUM, "M", "M-07");
        open("/register/ORB-AVR-001?size=M");
        page.getByTestId("cell-M-07").click();
        PlaywrightAssertions.assertThat(page.getByTestId("selection")).containsText("Reserved");
        assertNoWcagViolations("reserved compartment selected");

        page.getByTestId("cell-M-11").click();
        PlaywrightAssertions.assertThat(page.getByTestId("selection")).containsText("Available to register");
        assertNoWcagViolations("free compartment selected");

        page.getByTestId("continue").click();
        page.getByTestId("sender-name").fill("Ana Silva");
        page.getByTestId("sender-email").fill("not-an-email");
        page.getByTestId("sender-phone").fill("12");
        page.getByTestId("recipient-name").focus();
        PlaywrightAssertions.assertThat(page.locator("#s-email-err")).hasText("Please enter a valid email address.");
        PlaywrightAssertions.assertThat(page.locator("#s-phone-err")).not().isEmpty();
        assertNoWcagViolations("validation errors");

        page.getByTestId("sender-email").fill("ana.silva@example.com");
        page.getByTestId("sender-phone").fill("+351 912 345 678");
        page.getByTestId("recipient-name").fill("João Santos");
        page.getByTestId("recipient-email").fill("joao.santos@example.com");
        page.getByTestId("recipient-phone").fill("+351 934 567 890");
        page.getByTestId("terms").check();
        page.getByTestId("submit").click();
        PlaywrightAssertions.assertThat(page.getByTestId("confirmation")).containsText("Compartment M-11 reserved");
        assertNoWcagViolations("registration confirmation");
    }

    @Test
    @DisplayName("NFR2: tracking and operator pages, in their interactive states")
    void nfr2_trackingAndOperatorStatesMeetWcag22LevelAa() {
        open("/tracking");
        page.getByTestId("tracking-input").fill("ORB-261024-071X");
        page.getByTestId("track-button").click();
        PlaywrightAssertions.assertThat(page.getByTestId("tracking-error")).containsText("Invalid tracking code");
        assertNoWcagViolations("invalid tracking code");

        register(FORUM, "M", "M-07");
        open("/operator/stations/ORB-AVR-001");
        PlaywrightAssertions.assertThat(page.getByTestId("parcel-row")).hasCount(1);
        page.getByTestId("cell-M-15").click();
        PlaywrightAssertions.assertThat(page.getByTestId("oos-reason")).isVisible();
        assertNoWcagViolations("operator compartment panel");

        page.getByTestId("oos-reason").fill("Door sensor faulty");
        page.getByTestId("out-of-service").click();
        PlaywrightAssertions.assertThat(page.getByTestId("incidents")).containsText("M-15");
        assertNoWcagViolations("operator after putting a compartment out of service");
    }

    @Test
    @DisplayName("NFR2: every terminal screen of a drop-off and a pickup, including a wrong code")
    void nfr2_terminalDropOffAndPickupScreensMeetWcag22LevelAa() {
        RegistrationResult reg = register(FORUM, "M", "M-07");
        collect(reg);
        open("/terminal/ORB-AVR-001");

        page.getByTestId("start-dropoff").click();
        PlaywrightAssertions.assertThat(page.getByTestId("pin-input")).isVisible();
        assertNoWcagViolations("drop-off code entry");

        enterPin("000000".equals(reg.dropOffCode()) ? "000001" : "000000");
        PlaywrightAssertions.assertThat(page.getByTestId("pin-error")).containsText("2 attempt(s) left");
        assertNoWcagViolations("wrong code message");

        enterPin(reg.dropOffCode());
        PlaywrightAssertions.assertThat(page.getByTestId("screen-title")).hasText("Compartment M-07 is open");
        assertNoWcagViolations("compartment open (drop-off)");

        page.getByTestId("door-closed").click();
        PlaywrightAssertions.assertThat(page.getByTestId("screen-title")).hasText("Parcel stored");
        assertNoWcagViolations("parcel stored");

        String pickup = pickupCode(reg.trackingCode());
        page.getByTestId("finish").click();
        page.getByTestId("start-pickup").click();
        enterPin(pickup);
        PlaywrightAssertions.assertThat(page.getByTestId("screen-title")).hasText("Compartment M-07 is open");
        assertNoWcagViolations("compartment open (pickup)");

        page.getByTestId("door-closed").click();
        PlaywrightAssertions.assertThat(page.getByTestId("screen-title")).hasText("Enjoy your parcel!");
        assertNoWcagViolations("pickup completed");
    }

    @Test
    @DisplayName("NFR2: terminal screens for a door failure, the replacement compartment and a blocked terminal")
    void nfr2_terminalFailureScreensMeetWcag22LevelAa() {
        RegistrationResult reg = register(FORUM, "M", "M-07");
        collect(reg);
        hardware.inject(new CompartmentRef(FORUM, "M-07"), FakeLockerHardware.Fault.DOOR_WONT_OPEN);
        open("/terminal/ORB-AVR-001");

        page.getByTestId("start-dropoff").click();
        enterPin(reg.dropOffCode());
        PlaywrightAssertions.assertThat(page.getByTestId("screen-title")).hasText("The compartment did not open");
        assertNoWcagViolations("door did not open");

        page.getByTestId("request-replacement").click();
        PlaywrightAssertions.assertThat(page.getByTestId("screen-title")).containsText("New compartment: M-");
        assertNoWcagViolations("replacement compartment issued");

        open("/terminal/ORB-AVR-001");
        page.getByTestId("start-pickup").click();
        enterPin("000000");
        PlaywrightAssertions.assertThat(page.getByTestId("pin-error")).containsText("2 attempt(s) left");
        enterPin("000001");
        PlaywrightAssertions.assertThat(page.getByTestId("pin-error")).containsText("1 attempt(s) left");
        enterPin("000002");
        PlaywrightAssertions.assertThat(page.getByTestId("screen-title")).hasText("Code entry blocked");
        assertNoWcagViolations("code entry blocked");
    }

    @Test
    @DisplayName("NFR2: keyboard users can skip the navigation (WCAG 2.4.1 Bypass Blocks)")
    void nfr2_firstTabStopIsASkipLinkToTheMainContent() {
        open("/");

        page.keyboard().press("Tab");
        assertThat(page.evaluate("document.activeElement.textContent.trim()")).isEqualTo("Skip to content");
        page.keyboard().press("Enter");

        assertThat(page.url()).endsWith("#main");
    }

    // =================================================================== NFR3: pickup code from SecureRandom

    @Test
    @DisplayName("NFR3: the codes are generated by a SecureRandom")
    void nfr3_codeServiceUsesSecureRandom() {
        assertThat(ReflectionTestUtils.getField(codes, "random")).isInstanceOf(SecureRandom.class);
    }

    @Test
    @DisplayName("NFR3: every digit of the 6-digit code is uniformly distributed (chi-square, p = 0.0001)")
    void nfr3_codesAreSixUniformlyDistributedDigits() {
        int samples = 50_000;
        long[][] counts = new long[6][10];
        for (int i = 0; i < samples; i++) {
            String pin = codes.newPin();
            assertThat(pin).matches("\\d{6}");
            for (int pos = 0; pos < 6; pos++) {
                counts[pos][pin.charAt(pos) - '0']++;
            }
        }

        double expected = samples / 10.0;
        double criticalValue = 33.72;   // chi-square, 9 degrees of freedom, p = 0.0001
        for (int pos = 0; pos < 6; pos++) {
            double chiSquare = 0;
            for (long observed : counts[pos]) {
                chiSquare += (observed - expected) * (observed - expected) / expected;
            }
            assertThat(chiSquare).as("chi-square of digit %d", pos + 1).isLessThan(criticalValue);
        }
    }

    @Test
    @DisplayName("NFR3: the codes are stored only as a hash and never returned by the read endpoints")
    void nfr3_codesAreStoredHashedAndNotExposed() throws Exception {
        RegistrationResult reg = register(FORUM, "M", null);
        String tc = reg.trackingCode();
        String dropOffCode = reg.dropOffCode();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT drop_off_code_hash, pickup_code_hash FROM parcel WHERE tracking_code = ?", tc);
        assertThat(row.get("drop_off_code_hash")).isEqualTo(codes.hash(dropOffCode)).isNotEqualTo(dropOffCode);
        assertThat(get("/api/v1/parcels/" + tc).getResponse().getContentAsString()).doesNotContain(dropOffCode);
        assertThat(get("/api/v1/operator/parcels/" + tc).getResponse().getContentAsString()).doesNotContain(dropOffCode);

        String pickupCode = deliverToLocker(FORUM, reg);
        String stored = jdbc.queryForObject("SELECT pickup_code_hash FROM parcel WHERE tracking_code = ?", String.class, tc);
        assertThat(stored).isEqualTo(codes.hash(pickupCode)).isNotEqualTo(pickupCode);
    }

    // =================================================================== NFR4: OpenStreetMap usage policy

    /** The Nominatim client with the application's own settings, pointed at the WireMock server. */
    private NominatimGeocoder nominatim(MutableClock time) {
        OrbitProperties.Geocoding app = properties.geocoding();
        return new NominatimGeocoder(new OrbitProperties.Geocoding(app.provider(), nominatimServer.baseUrl(),
                app.userAgent(), app.countryCodes(), app.connectTimeout(), app.readTimeout(), app.minInterval(),
                app.cacheTtl(), app.negativeCacheTtl(), app.cacheMaxEntries()), time);
    }

    private int requestsToNominatim() {
        return nominatimServer.findAll(getRequestedFor(urlPathEqualTo("/search"))).size();
    }

    @Test
    @DisplayName("NFR4 rule 1: at most 1 request per second; extra requests fail fast and never reach OSM")
    void nfr4_atMostOneRequestPerSecond() {
        MutableClock time = MutableClock.fixedAt(NOW);
        NominatimGeocoder geocoder = nominatim(time);

        geocoder.geocode("Rua de Coimbra 10, Aveiro");
        time.advance(Duration.ofMillis(999));
        assertThatThrownBy(() -> geocoder.geocode("Universidade de Aveiro"))
                .isInstanceOfSatisfying(GeocodingUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(GeocodingUnavailableException.Reason.RATE_LIMITED));
        assertThat(requestsToNominatim()).isEqualTo(1);

        time.advance(Duration.ofMillis(1));
        geocoder.geocode("Universidade de Aveiro");
        assertThat(requestsToNominatim()).isEqualTo(2);
    }

    @Test
    @DisplayName("NFR4 rule 1: 20 simultaneous searches send a single request to OSM")
    void nfr4_simultaneousSearchesSendOneRequest() throws Exception {
        NominatimGeocoder geocoder = nominatim(MutableClock.fixedAt(NOW));
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String address = "Address " + i + ", Aveiro";
            results.add(pool.submit(() -> {
                start.await();
                try {
                    geocoder.geocode(address);
                    return true;
                } catch (GeocodingUnavailableException e) {
                    return false;
                }
            }));
        }

        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> r : results) {
            succeeded += r.get() ? 1 : 0;
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(requestsToNominatim()).isEqualTo(1);
    }

    @Test
    @DisplayName("NFR4 rule 2: the application identifies itself with its own User-Agent")
    void nfr4_requestsIdentifyTheApplication() {
        String userAgent = properties.geocoding().userAgent();

        nominatim(MutableClock.fixedAt(NOW)).geocode("Rua de Coimbra 10, Aveiro");

        assertThat(userAgent).isNotBlank().containsIgnoringCase("orbit").doesNotContainIgnoringCase("java");
        nominatimServer.verify(getRequestedFor(urlPathEqualTo("/search"))
                .withHeader("User-Agent", equalTo(userAgent))
                .withQueryParam("limit", equalTo("1")));
    }

    @Test
    @DisplayName("NFR4 rule 3: results are cached, so a repeated search is not sent to OSM again")
    void nfr4_repeatedSearchesAreServedFromTheCache() {
        MutableClock time = MutableClock.fixedAt(NOW);
        OrbitProperties.Geocoding cfg = properties.geocoding();
        CachingGeocoder geocoder = new CachingGeocoder(nominatim(time), time, cfg.cacheTtl(), cfg.negativeCacheTtl(),
                cfg.cacheMaxEntries());

        GeoPoint first = geocoder.geocode("Rua de Coimbra 10, Aveiro").orElseThrow();
        time.advance(Duration.ofMinutes(10));   // well past the rate limit, so only the cache can stop the request
        GeoPoint again = geocoder.geocode("  rua de COIMBRA 10,   aveiro ").orElseThrow();

        assertThat(again).isEqualTo(first);
        assertThat(requestsToNominatim()).isEqualTo(1);
    }

    @Test
    @DisplayName("NFR4 rule 4: no autocomplete; the address is geocoded only when the user submits the search")
    void nfr4_noAutocompleteSearch() {
        List<String> geocodingCalls = new CopyOnWriteArrayList<>();
        page.onRequest(r -> {
            if (r.url().contains("/api/v1/stations/nearby")) {
                geocodingCalls.add(r.url());
            }
        });
        open("/");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        page.getByTestId("address-input").pressSequentially("Rua de Coimbra 10, Aveiro",
                new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(20));
        page.waitForTimeout(500);
        assertThat(geocodingCalls).as("requests while typing").isEmpty();

        page.waitForResponse(r -> r.url().contains("/api/v1/stations/nearby"),
                () -> page.getByTestId("address-input").press("Enter"));
        assertThat(geocodingCalls).hasSize(1);
    }

    /** A stand-in for Leaflet that records the options the pages pass to it (the real one comes from a CDN). */
    private static final String FAKE_LEAFLET = """
            (function () {
              const chain = () => new Proxy(function () {}, {
                get: (t, p) => (p === 'then' ? undefined : chain()), apply: () => chain() });
              window.__maps = []; window.__tiles = [];
              window.L = new Proxy({}, { get: (t, p) => {
                if (p === 'map') return (el, o) => { window.__maps.push(o); return chain(); };
                if (p === 'tileLayer') return (url, o) => { window.__tiles.push({ url, attribution: o.attribution }); return chain(); };
                return chain();
              } });
            })();""";

    @ParameterizedTest(name = "NFR4 rule 5 {0}")
    @ValueSource(strings = {"/", "/register", "/register/ORB-AVR-001?size=M", "/operator", "TRACKING_DETAIL",
            "OPERATOR_PARCEL"})
    @DisplayName("NFR4 rule 5: every map shows the OpenStreetMap attribution")
    void nfr4_everyMapShowsTheAttribution(String path) {
        if (path.equals("TRACKING_DETAIL") || path.equals("OPERATOR_PARCEL")) {
            String tc = register(FORUM, "M", null).trackingCode();
            path = path.equals("TRACKING_DETAIL") ? "/tracking/" + tc : "/operator/parcels/" + tc;
        }
        page.route("**/leaflet.min.js", route -> route.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                .setContentType("application/javascript").setBody(FAKE_LEAFLET)));

        open(path);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(page.evaluate("window.__maps.length")).as("maps on the page").isNotEqualTo(0);
        assertThat(page.evaluate("window.__maps.every(o => o.attributionControl === true)")).isEqualTo(true);
        assertThat(page.evaluate("window.__tiles.every(t => t.attribution.includes('OpenStreetMap contributors'))"))
                .isEqualTo(true);
    }

    @ParameterizedTest(name = "NFR4 rule 6: HTTP {0} from OSM")
    @ValueSource(ints = {429, 403})
    @DisplayName("NFR4 rule 6: when OSM refuses the request (usage limit or blocked), the failure is reported, not retried")
    void nfr4_refusalIsRateLimitedAndNotRetried(int httpStatus) {
        nominatimServer.stubFor(WireMock.get(urlPathEqualTo("/search")).willReturn(status(httpStatus)));

        assertThatThrownBy(() -> nominatim(MutableClock.fixedAt(NOW)).geocode("Rua de Coimbra 10, Aveiro"))
                .isInstanceOfSatisfying(GeocodingUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo(GeocodingUnavailableException.Reason.RATE_LIMITED));
        assertThat(requestsToNominatim()).isEqualTo(1);
    }

    @Test
    @DisplayName("NFR4 rule 6: when the provider is unavailable, the user still gets every station")
    void nfr4_providerUnavailableFallsBackToAllStations() {
        ((StubGeocoder) geocoder).setUnavailable(true);

        assertThat(get("/api/v1/stations/nearby?address=Aveiro")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyJson().extractingPath("$.fallback").isEqualTo("/api/v1/stations");
        open("/");
        page.getByTestId("address-input").fill("Rua de Coimbra 10, Aveiro");
        page.getByTestId("search-button").click();

        PlaywrightAssertions.assertThat(page.locator("[data-alert]")).containsText("temporarily unavailable");
        PlaywrightAssertions.assertThat(page.getByTestId("results").locator(".station-card")).not().hasCount(0);
    }

    @Test
    @DisplayName("NFR4 rule 7: tests and load tests never call the real OSM server")
    void nfr4_testsAndLoadTestsUseTheStub() {
        Properties loadtest = loadYaml("application-loadtest.yml");
        Properties test = loadYaml("application-test.yml");

        assertThat(geocoder).isInstanceOf(StubGeocoder.class);
        assertThat(test.getProperty("orbit.geocoding.provider")).isEqualTo("stub");
        assertThat(loadtest.getProperty("orbit.geocoding.provider")).isEqualTo("stub");
    }

    private static Properties loadYaml(String resource) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(resource));
        return yaml.getObject();
    }

    // =================================================================== NFR6: a code can be used only once

    @Test
    @DisplayName("NFR6: a pickup code opens the compartment once; the second use is an invalid code")
    void nfr6_pickupCodeWorksOnlyOnce() {
        String pickupCode = deliverToLocker(FORUM, register(FORUM, "M", null));
        String body = "{\"code\":\"" + pickupCode + "\"}";

        assertThat(post("/api/v1/terminals/" + FORUM + "/pickups", body)).hasStatusOk()
                .bodyJson().extractingPath("$.outcome").isEqualTo("DOOR_OPENED");
        assertThat(post("/api/v1/terminals/" + FORUM + "/pickups", body))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_CODE");
    }

    @Test
    @DisplayName("NFR6: a drop-off code opens the compartment once; after the deposit it is an invalid code")
    void nfr6_dropOffCodeWorksOnlyOnce() {
        RegistrationResult reg = register(FORUM, "M", null);
        deliverToLocker(FORUM, reg);

        assertThat(post("/api/v1/terminals/" + FORUM + "/drop-offs",
                "{\"code\":\"" + reg.dropOffCode() + "\"}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_CODE");
    }

    // =================================================================== NFR7: client refresh at least every 2 min

    @ParameterizedTest(name = "NFR7 {0}")
    @CsvSource({
            "/,                                  /api/v1/stations",
            "/register?size=M,                   /api/v1/stations",
            "/register/ORB-AVR-001?size=M,      /api/v1/stations/ORB-AVR-001",
            "/operator,                          /api/v1/operator/parcels/recent",
            "/operator/stations/ORB-AVR-001,     /api/v1/operator/stations/ORB-AVR-001/parcels",
            "TRACKING_DETAIL,                    /api/v1/parcels/",
            "OPERATOR_PARCEL,                    /api/v1/operator/parcels/"})
    @DisplayName("NFR7: the page asks the server for fresh data in every 2-minute window")
    void nfr7_clientRefreshesAtLeastEveryTwoMinutes(String path, String api) {
        if (path.equals("TRACKING_DETAIL") || path.equals("OPERATOR_PARCEL")) {
            String tc = register(FORUM, "M", null).trackingCode();
            path = path.equals("TRACKING_DETAIL") ? "/tracking/" + tc : "/operator/parcels/" + tc;
            api = api + tc;
        }
        String expectedApi = api;
        Predicate<Request> refresh = r -> r.url().contains(expectedApi);
        page.clock().install();
        open(path);
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // three consecutive 2-minute windows (fake time), each must contain a request for fresh data
        for (int window = 0; window < 3; window++) {
            page.waitForRequest(refresh, new Page.WaitForRequestOptions().setTimeout(5000),
                    () -> page.clock().runFor(120_000));
        }
    }

    // =================================================================== NFR8: lockout after 3 wrong attempts

    private MvcTestResult get(String uri) {
        return tester.get().uri(uri).exchange();
    }

    private MvcTestResult post(String uri, String body) {
        return tester.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult pickup(String station, String code) {
        return post("/api/v1/terminals/" + station + "/pickups", "{\"code\":\"" + code + "\"}");
    }

    private static String otherThan(String code) {
        return "%06d".formatted((Integer.parseInt(code) + 1) % 1_000_000);
    }

    @Test
    @DisplayName("NFR8: the third consecutive wrong code blocks the locker, even for the right code")
    void nfr8_threeWrongCodesBlockTheLocker() {
        String pickupCode = deliverToLocker(FORUM, register(FORUM, "M", null));
        String wrong = otherThan(pickupCode);

        assertThat(pickup(FORUM, wrong)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.remainingAttempts").isEqualTo(2);
        assertThat(pickup(FORUM, wrong)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.remainingAttempts").isEqualTo(1);
        assertThat(pickup(FORUM, wrong)).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .bodyJson().extractingPath("$.code").isEqualTo("TOO_MANY_ATTEMPTS");

        assertThat(pickup(FORUM, pickupCode)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(stateOf(jdbc.queryForObject("SELECT tracking_code FROM parcel", String.class)))
                .isEqualTo(ParcelState.READY_FOR_PICKUP);
    }

    @Test
    @DisplayName("NFR8: only consecutive wrong codes count; a right code resets the count")
    void nfr8_rightCodeResetsTheCount() {
        String first = deliverToLocker(FORUM, register(FORUM, "M", null));
        String second = deliverToLocker(FORUM, register(FORUM, "S", null));
        String wrong = "%06d".formatted((Integer.parseInt(first) + Integer.parseInt(second) + 1) % 1_000_000);
        wrong = wrong.equals(first) || wrong.equals(second) ? otherThan(wrong) : wrong;

        pickup(FORUM, wrong);
        pickup(FORUM, wrong);
        assertThat(pickup(FORUM, first)).hasStatusOk();

        assertThat(pickup(FORUM, wrong)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(pickup(FORUM, wrong)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(pickup(FORUM, second)).hasStatusOk();
    }

    @Test
    @DisplayName("NFR8: the block applies to that locker only and ends after one hour")
    void nfr8_blockIsPerLockerAndLastsOneHour() {
        for (int i = 0; i < 3; i++) {
            pickup(FORUM, "00000" + i);
        }

        assertThat(pickup("ORB-AVR-002", "000009")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        clock.advance(Duration.ofMinutes(59));
        assertThat(pickup(FORUM, "000009")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        clock.advance(Duration.ofMinutes(1));
        assertThat(pickup(FORUM, "000009")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
