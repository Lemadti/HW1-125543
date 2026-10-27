package pt.ua.deti.tqs.orbit.bdd;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitUntilState;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.ua.deti.tqs.orbit.adapter.geocoding.StubGeocoder;
import pt.ua.deti.tqs.orbit.adapter.hardware.FakeLockerHardware;
import pt.ua.deti.tqs.orbit.adapter.notification.NotificationOutbox;
import pt.ua.deti.tqs.orbit.config.MutableClock;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.port.CompartmentRef;
import pt.ua.deti.tqs.orbit.port.GeocoderPort;
import pt.ua.deti.tqs.orbit.port.Notification;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;
import pt.ua.deti.tqs.orbit.service.AttemptGuard;
import pt.ua.deti.tqs.orbit.service.DeadlineService;
import pt.ua.deti.tqs.orbit.service.ParcelService;
import pt.ua.deti.tqs.orbit.service.RegisterParcelCommand;
import pt.ua.deti.tqs.orbit.service.TerminalService;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;
import pt.ua.deti.tqs.orbit.support.AbstractIntegrationTest;
import pt.ua.deti.tqs.orbit.support.TestData;

/**
 * Step definitions for the acceptance tests (features in {@code src/test/resources/features}): the whole application
 * on a random port, a real PostgreSQL, and a real browser.
 *
 * <p>Front door / back door (as in Labs 05/06): what the user does and sees goes through the <strong>browser</strong>
 * ({@code page}), with the locators a user would use (labels, roles, visible text). Arranging data ("a parcel waiting
 * for pickup") goes through the <strong>back door</strong> — the services, because the PINs exist in clear only when
 * they are generated — and checking what was stored ("the parcel should be stored as …") goes through the
 * repositories. Time and hardware are controlled through the injected fakes ({@link MutableClock},
 * {@link FakeLockerHardware}), so time-based and hardware-failure scenarios are deterministic.</p>
 */
public class ParcelSteps {

    /** Set to false (and SLOW_MO to e.g. 500) to watch the scenarios run. */
    private static final boolean HEADLESS = true;
    private static final double SLOW_MO = 0;
    private static final Pattern CODE = Pattern.compile("[Cc]ode: (\\d{6})");

    // One browser for the whole run (static); a new context and page for every scenario.
    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;

    // Cucumber-Spring creates this class as a Spring bean for every scenario: injection works here.
    @LocalServerPort
    private int port;
    @Autowired
    private ParcelService parcelService;
    @Autowired
    private TerminalService terminalService;
    @Autowired
    private DeadlineService deadlines;
    @Autowired
    private ParcelRepository parcels;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MutableClock clock;
    @Autowired
    private FakeLockerHardware hardware;
    @Autowired
    private NotificationOutbox outbox;
    @Autowired
    private AttemptGuard guard;
    @Autowired
    private GeocoderPort geocoder;

    private String station = AbstractIntegrationTest.FORUM;
    private RegistrationResult registration;
    private RegistrationResult newRegistration;
    private String pickupCode;

    // ------------------------------------------------------------------ hooks

    @BeforeAll // io.cucumber.java.BeforeAll - not JUnit's
    public static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(HEADLESS).setSlowMo(SLOW_MO));
        PlaywrightAssertions.setDefaultAssertionTimeout(7000);
    }

    @AfterAll
    public static void closeBrowser() {
        playwright.close(); // also closes the browser
    }

    /** Every scenario starts from the seed data, with the clock at a fixed instant, no faults and a fresh context. */
    @Before
    public void startScenario() {
        AbstractIntegrationTest.resetDatabase(jdbc);
        clock.setFixed(AbstractIntegrationTest.NOW);
        hardware.reset();
        hardware.setAutoClose(true);
        outbox.clear();
        guard.resetAll();
        ((StubGeocoder) geocoder).setUnavailable(false);

        context = browser.newContext(new Browser.NewContextOptions().setLocale("en-GB"));
        // hermetic: map tiles, CDN and fonts are never fetched
        context.route("**/*", route -> {
            if (route.request().url().startsWith(url(""))) {
                route.resume();
            } else {
                route.abort();
            }
        });
        page = context.newPage();
    }

    @After
    public void endScenario() {
        context.close();
        clock.useSystemTime();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private void open(String path) {
        page.navigate(url(path), new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    }

    /** The code field, by its accessible name (the kiosk's main region is named after the same heading text). */
    private Locator pinField(String name) {
        return page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(name));
    }

    private Locator button(String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
    }

    private Locator exactButton(String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private Locator stationCards() {
        return page.getByTestId("results").locator("[data-testid=station-card]");
    }

    private String latestCode(String trackingCode, Notification.Type type) {
        return outbox.latest().stream()
                .filter(e -> e.notification().type() == type && trackingCode.equals(e.notification().trackingCode()))
                .map(e -> CODE.matcher(e.notification().body()))
                .filter(Matcher::find).map(m -> m.group(1)).findFirst().orElseThrow();
    }

    private RegistrationResult registerInBackDoor(String size, String station, String compartment) {
        return parcelService.register(new RegisterParcelCommand(station, CompartmentSize.valueOf(size), compartment,
                TestData.SENDER, TestData.RECIPIENT));
    }

    private void dropOffInBackDoor(RegistrationResult reg) {
        String token = terminalService.startDropOff(station, reg.dropOffCode()).sessionToken();
        terminalService.confirmDropOff(station, token);
    }

    private long activeParcelsIn(String compartment) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM parcel p JOIN compartment c ON c.id = p.compartment_id JOIN station s ON s.id = c.station_id
                WHERE s.code = ? AND c.code = ? AND p.state IN ('CREATED','ON_ITS_WAY','READY_FOR_PICKUP')""",
                Long.class, station, compartment);
    }

    // ------------------------------------------------------------------ Given (back door)

    @Given("the geocoding provider is unavailable")
    public void theGeocodingProviderIsUnavailable() {
        ((StubGeocoder) geocoder).setUnavailable(true);
    }

    @Given("a {string} parcel is registered at station {string} in compartment {string}")
    public void aParcelIsRegistered(String size, String station, String compartment) {
        this.station = station;
        registration = registerInBackDoor(size, station, compartment);
    }

    @Given("compartment {string} of station {string} is reserved")
    public void compartmentIsReserved(String compartment, String station) {
        this.station = station;
        registerInBackDoor(compartment.substring(0, 1), station, compartment);
    }

    @Given("all {string} compartments of station {string} are reserved")
    public void allCompartmentsAreReserved(String size, String station) {
        this.station = station;
        List<String> free = jdbc.queryForList("""
                SELECT c.code FROM compartment c JOIN station s ON s.id = c.station_id
                WHERE s.code = ? AND c.size = ? AND c.status = 'IN_SERVICE' ORDER BY c.position""", String.class, station, size);
        free.forEach(code -> registerInBackDoor(size, station, code));
    }

    @Given("the courier collected the parcel")
    public void theCourierCollectedTheParcel() {
        parcelService.collect(registration.deliveryCode(), null);
    }

    @Given("a {string} parcel is waiting for pickup at station {string} in compartment {string}")
    public void aParcelIsWaitingForPickup(String size, String station, String compartment) {
        aParcelIsRegistered(size, station, compartment);
        theCourierCollectedTheParcel();
        dropOffInBackDoor(registration);
        pickupCode = latestCode(registration.trackingCode(), Notification.Type.PICKUP_CODE);
    }

    @Given("the recipient already picked up the parcel")
    public void theRecipientAlreadyPickedUpTheParcel() {
        String token = terminalService.startPickup(station, pickupCode).sessionToken();
        terminalService.confirmPickup(station, token);
    }

    @Given("the door of compartment {string} will not open")
    public void theDoorWillNotOpen(String compartment) {
        hardware.inject(new CompartmentRef(station, compartment), FakeLockerHardware.Fault.DOOR_WONT_OPEN);
    }

    @Given("the locker is not responding")
    public void theLockerIsNotResponding() {
        hardware.setStationUnreachable(true);
    }

    @Given("{int} day(s) pass(es)")
    public void daysPass(int days) {
        clock.advance(Duration.ofDays(days));
        deadlines.processDeadlines();
    }

    @When("{int} hour(s) pass(es)")
    public void hoursPass(int hours) {
        clock.advance(Duration.ofHours(hours));
        deadlines.processDeadlines();
    }

    @When("the drop-off deadline passes")
    public void theDropOffDeadlinePasses() {
        clock.setFixed(registration.dropOffDeadline().plusSeconds(1));
        deadlines.processDeadlines();
    }

    @When("another parcel is registered for compartment {string}")
    public void anotherParcelIsRegistered(String compartment) {
        newRegistration = registerInBackDoor(compartment.substring(0, 1), station, compartment);
        parcelService.collect(newRegistration.deliveryCode(), null);
    }

    // ------------------------------------------------------------------ When (front door): US1

    @When("I search for stations near {string}")
    public void iSearchForStationsNear(String address) {
        open("/");
        page.getByLabel("Search near an address").fill(address);
        exactButton("Search").click();
    }

    @When("I choose a radius of {int} km")
    public void iChooseARadius(int km) {
        // the radius re-runs the current search: wait until the search has answered
        assertThat(page.getByTestId("result-count")).containsText("nearby");
        page.getByLabel("Radius").selectOption(String.valueOf(km));
    }

    @When("I select the station {string}")
    public void iSelectTheStation(String name) {
        stationCards().filter(new Locator.FilterOptions().setHasText(name)).click();
    }

    // ------------------------------------------------------------------ When (front door): US2

    @Given("I am registering a {string} parcel at station {string}")
    public void iAmRegisteringAParcel(String size, String stationName) {
        open("/register");
        page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName(size)).click();
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(stationName)).click();
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(stationName))).isVisible();
    }

    @When("I choose compartment {string}")
    public void iChooseCompartment(String compartment) {
        button(compartment + ",").click();
    }

    @When("I fill in the sender and recipient contacts and accept the terms")
    public void iFillInTheContacts() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Register").setExact(true)).click();
        Locator sender = page.getByRole(AriaRole.GROUP, new Page.GetByRoleOptions().setName("Sender contact"));
        Locator recipient = page.getByRole(AriaRole.GROUP, new Page.GetByRoleOptions().setName("Recipient contact"));
        sender.getByLabel("Full name").fill("Ana Silva");
        sender.getByLabel("Email address").fill("ana.silva@example.com");
        sender.getByLabel("Phone number").fill("+351 912 345 678");
        recipient.getByLabel("Full name").fill("João Santos");
        recipient.getByLabel("Email address").fill("joao.santos@example.com");
        recipient.getByLabel("Phone number").fill("+351 934 567 890");
        page.getByLabel("I agree to the locker reservation terms").check();
    }

    @When("I submit the registration")
    public void iSubmitTheRegistration() {
        button("Submit registration").click();
    }

    @When("I look for a {string} compartment near {string}")
    public void iLookForACompartmentNear(String size, String address) {
        open("/register");
        page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName(size)).click();
        page.getByLabel("Where do you need a locker?").fill(address);
        exactButton("Search").click();
    }

    // ------------------------------------------------------------------ When (front door): courier and terminal

    @When("the courier confirms the collection of the parcel on the courier page")
    public void theCourierConfirmsTheCollection() {
        open("/courier");
        page.getByLabel("Delivery code").fill(registration.deliveryCode());
        button("Confirm collection").click();
    }

    /** Types a code on the terminal of the scenario's station, starting from the welcome screen. */
    private void enterCodeAtTheTerminal(String choice, String label, String code) {
        if (!page.url().endsWith("/terminal/" + station)) {
            open("/terminal/" + station);
        } else if (exactButton("Finish").isVisible()) {
            exactButton("Finish").click();
        }
        button(choice).click();
        pinField(label).fill(code);
        button("Open compartment").click();
    }

    @When("the courier enters the drop-off PIN at the terminal")
    public void theCourierEntersTheDropOffPin() {
        enterCodeAtTheTerminal("Drop off a parcel", "Enter the drop-off PIN", registration.dropOffCode());
    }

    @When("the recipient enters the pickup code at the terminal")
    public void theRecipientEntersThePickupCode() {
        enterCodeAtTheTerminal("Collect a parcel", "Enter your pickup code", pickupCode);
    }

    @When("someone enters {int} wrong pickup codes at the terminal")
    public void someoneEntersWrongPickupCodes(int n) {
        for (int i = 0; i < n; i++) {
            String wrong = String.format("%06d", (Integer.parseInt(pickupCode) + 1 + i) % 1_000_000);
            if (i == 0) {
                enterCodeAtTheTerminal("Collect a parcel", "Enter your pickup code", wrong);
            } else {
                // stay on the code screen: wait for the previous answer, then type the next code
                assertThat(page.getByRole(AriaRole.ALERT)).containsText((n - i) + " attempt(s) left");
                pinField("Enter your pickup code").fill(wrong);
                button("Open compartment").click();
            }
        }
    }

    @When("the courier closes the door")
    @When("the recipient closes the door")
    public void closesTheDoor() {
        button("I have closed the door").click();
    }

    @When("the courier asks for another compartment")
    public void theCourierAsksForAnotherCompartment() {
        button("Use another compartment").click();
    }

    @When("the courier drops the new parcel off at the terminal")
    public void theCourierDropsTheNewParcelOff() {
        enterCodeAtTheTerminal("Drop off a parcel", "Enter the drop-off PIN", newRegistration.dropOffCode());
        closesTheDoor();
    }

    // ------------------------------------------------------------------ When (front door): US5, US6

    @When("I track the parcel")
    public void iTrackTheParcel() {
        iTrackTheParcel(registration.trackingCode());
    }

    @When("I track the parcel {string}")
    public void iTrackTheParcel(String trackingCode) {
        open("/tracking");
        page.getByLabel("Tracking code").fill(trackingCode);
        button("Track parcel").click();
    }

    @When("the operator shows the {string} parcels of station {string}")
    public void theOperatorShowsTheParcels(String filter, String station) {
        open("/operator/stations/" + station);
        exactButton(filter).click();
    }

    @When("the operator registers the removal of the parcel")
    public void theOperatorRegistersTheRemoval() {
        parcelRow().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Register removal")).click();
    }

    @When("the operator puts compartment {string} of station {string} out of service because {string}")
    public void theOperatorPutsACompartmentOutOfService(String compartment, String station, String reason) {
        this.station = station;
        open("/operator/stations/" + station);
        button(compartment + ",").click();
        page.getByLabel("Reason").fill(reason);
        exactButton("Put out of service").click();
    }

    private Locator parcelRow() {
        return page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(registration.trackingCode()));
    }

    // ------------------------------------------------------------------ Then (front door: what the user sees)

    /** Exact text: "5 stations" must not be satisfied by "15 stations". */
    @Then("I should see {string}")
    public void iShouldSee(String text) {
        assertThat(page.getByText(text, new Page.GetByTextOptions().setExact(true)).first()).isVisible();
    }

    /** Part of a message (alerts and status lines carry more text than the scenario needs to claim). */
    @Then("I should see a message saying {string}")
    public void iShouldSeeAMessageSaying(String text) {
        assertThat(page.getByText(text).first()).isVisible();
    }

    @Then("the terminal should show {string}")
    public void theTerminalShouldShow(String title) {
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1))).hasText(title);
    }

    @Then("the stations should be, in this order:")
    public void theStationsShouldBeInThisOrder(List<String> names) {
        assertThat(stationCards()).hasCount(names.size());
        assertThat(stationCards()).containsText(names.toArray(new String[0]));
    }

    @Then("every station should show how many lockers are available")
    public void everyStationShowsAvailability() {
        int n = stationCards().count();
        for (int i = 0; i < n; i++) {
            assertThat(stationCards().nth(i)).containsText("available");
        }
    }

    @Then("{string} should not be listed")
    public void shouldNotBeListed(String name) {
        assertThat(stationCards().filter(new Locator.FilterOptions().setHasText(name))).hasCount(0);
    }

    @Then("{int} stations should still be listed")
    public void stationsShouldStillBeListed(int n) {
        assertThat(stationCards()).hasCount(n);
    }

    @Then("I should see {int} compartments")
    public void iShouldSeeCompartments(int n) {
        assertThat(page.getByRole(AriaRole.GROUP, new Page.GetByRoleOptions().setName("Compartments, front view"))
                .getByRole(AriaRole.BUTTON)).hasCount(n);
    }

    @Then("compartment {string} should be shown as {string}")
    public void compartmentShouldBeShownAs(String compartment, String availability) {
        assertThat(button(compartment + ",")).hasAccessibleName(Pattern.compile(", " + availability + "$"));
    }

    @Then("I should see a tracking code, a delivery code and a 6-digit drop-off PIN")
    public void iShouldSeeTheCodes() {
        assertThat(page.getByTestId("tracking-code")).hasText(Pattern.compile("ORB-\\d{6}-\\d{4}"));
        assertThat(page.getByTestId("delivery-code")).hasText(Pattern.compile("DLV-\\w{12}"));
        assertThat(page.getByTestId("drop-off-code")).hasText(Pattern.compile("\\d{6}"));
    }

    @Then("I should not be able to continue the registration")
    public void iShouldNotBeAbleToContinue() {
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Register").setExact(true)))
                .hasCount(0);
    }

    @Then("the timeline should be, in this order:")
    public void theTimelineShouldBeInThisOrder(List<String> steps) {
        assertThat(page.getByTestId("timeline").getByRole(AriaRole.LISTITEM)).containsText(steps.toArray(new String[0]));
    }

    @Then("the parcel should be listed")
    public void theParcelShouldBeListed() {
        assertThat(parcelRow()).hasCount(1);
    }

    @Then("I should see {string} in the malfunction reports")
    public void iShouldSeeInTheMalfunctionReports(String text) {
        assertThat(page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Malfunction reports")))
                .containsText(text);
    }

    @Then("the new drop-off PIN sent to the sender should open compartment {string}")
    public void theNewDropOffPinShouldOpen(String compartment) {
        String newPin = latestCode(registration.trackingCode(), Notification.Type.REPLACEMENT_DROP_OFF_CODE);
        Assertions.assertThat(newPin).isNotEqualTo(registration.dropOffCode());
        enterCodeAtTheTerminal("Drop off a parcel", "Enter the drop-off PIN", newPin);
        theTerminalShouldShow("Compartment " + compartment + " is open");
    }

    // ------------------------------------------------------------------ Then (back door: what was stored)

    @Then("the parcel should be stored as {string}")
    public void theParcelShouldBeStoredAs(String state) {
        Assertions.assertThat(parcels.findByTrackingCode(registration.trackingCode()).orElseThrow().getState())
                .hasToString(state);
    }

    @Then("the parcel should be stored as {string} in compartment {string}")
    public void theParcelShouldBeStoredIn(String state, String compartment) {
        String trackingCode = page.getByTestId("tracking-code").textContent();
        Assertions.assertThat(parcels.findByTrackingCode(trackingCode).orElseThrow().getState()).hasToString(state);
        Assertions.assertThat(activeParcelsIn(compartment)).isEqualTo(1);
    }

    @Then("the parcel should be stored as {string} by {string}")
    public void theParcelShouldBeStoredAsBy(String state, String actor) {
        theParcelShouldBeStoredAs(state);
        Assertions.assertThat(jdbc.queryForObject("""
                SELECT t.actor FROM parcel_transition t JOIN parcel p ON p.id = t.parcel_id
                WHERE p.tracking_code = ? ORDER BY t.id DESC LIMIT 1""", String.class, registration.trackingCode()))
                .isEqualTo(actor);
    }

    @Then("the expired parcel should be stored as {string}")
    public void theExpiredParcelShouldBeStoredAs(String state) {
        theParcelShouldBeStoredAs(state);
    }

    @Then("the drop-off deadline should be {int} working days after the registration")
    public void theDropOffDeadlineShouldBe(int workingDays) {
        // registered at AbstractIntegrationTest.NOW, Saturday 24 Oct 2026 09:00 Lisbon -> Friday 6 Nov 2026 09:00
        Assertions.assertThat(workingDays).isEqualTo(10);
        Assertions.assertThat(jdbc.queryForObject("SELECT drop_off_deadline FROM parcel", java.sql.Timestamp.class)
                .toInstant()).isEqualTo(TestData.DROP_OFF_DEADLINE);
    }

    @Then("compartment {string} should be stored as {string}")
    public void compartmentShouldBeStoredAs(String compartment, String status) {
        Assertions.assertThat(jdbc.queryForObject("""
                SELECT c.status FROM compartment c JOIN station s ON s.id = c.station_id
                WHERE s.code = ? AND c.code = ?""", String.class, station, compartment)).isEqualTo(status);
    }

    @Then("compartment {string} should be free again")
    public void compartmentShouldBeFreeAgain(String compartment) {
        Assertions.assertThat(activeParcelsIn(compartment)).isZero();
    }

    @Then("the operator should be notified of a {string} in compartment {string}")
    public void theOperatorShouldBeNotified(String type, String compartment) {
        Assertions.assertThat(jdbc.queryForList("""
                SELECT i.type FROM incident i JOIN compartment c ON c.id = i.compartment_id WHERE c.code = ?""",
                String.class, compartment)).containsExactly(type);
        Assertions.assertThat(outbox.latest())
                .anySatisfy(e -> Assertions.assertThat(e.notification().type()).isEqualTo(Notification.Type.OPERATOR_ALERT));
    }

    @Then("the recipient should receive a 6-digit pickup code different from the drop-off PIN")
    public void theRecipientShouldReceiveAPickupCode() {
        pickupCode = latestCode(registration.trackingCode(), Notification.Type.PICKUP_CODE);
        Assertions.assertThat(pickupCode).matches("\\d{6}").isNotEqualTo(registration.dropOffCode());
        Assertions.assertThat(outbox.latest().get(0).notification().toEmail()).isEqualTo(TestData.RECIPIENT.getEmail());
    }

    @Then("the stored history should be:")
    public void theStoredHistoryShouldBe(List<Map<String, String>> expected) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT t.to_state, t.actor, t.occurred_at FROM parcel_transition t JOIN parcel p ON p.id = t.parcel_id
                WHERE p.tracking_code = ? ORDER BY t.id""", registration.trackingCode());
        Assertions.assertThat(rows).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            Assertions.assertThat(rows.get(i)).containsEntry("to_state", expected.get(i).get("to"))
                    .containsEntry("actor", expected.get(i).get("actor"));
            Assertions.assertThat(rows.get(i).get("occurred_at")).isNotNull();
        }
    }
}
