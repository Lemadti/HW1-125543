package pt.ua.deti.tqs.orbit.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import pt.ua.deti.tqs.orbit.adapter.geocoding.StubGeocoder;
import pt.ua.deti.tqs.orbit.adapter.hardware.FakeLockerHardware;
import pt.ua.deti.tqs.orbit.adapter.notification.NotificationOutbox;
import pt.ua.deti.tqs.orbit.config.MutableClock;
import pt.ua.deti.tqs.orbit.port.CompartmentRef;
import pt.ua.deti.tqs.orbit.port.GeocoderPort;
import pt.ua.deti.tqs.orbit.port.Notification;
import pt.ua.deti.tqs.orbit.service.AttemptGuard;
import pt.ua.deti.tqs.orbit.service.DeadlineService;
import pt.ua.deti.tqs.orbit.support.AbstractIntegrationTest;

/**
 * Step definitions drive the public REST API (the system boundary every actor uses), on the full application with
 * a real database. Time and hardware are controlled through the injected fakes (MutableClock, FakeLockerHardware),
 * so time-based and hardware-failure scenarios are deterministic.
 */
public class ParcelSteps {

    private static final Pattern CODE = Pattern.compile("[Cc]ode: (\\d{6})");

    @Autowired
    MockMvcTester mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MutableClock clock;
    @Autowired
    FakeLockerHardware hardware;
    @Autowired
    NotificationOutbox outbox;
    @Autowired
    AttemptGuard guard;
    @Autowired
    GeocoderPort geocoder;
    @Autowired
    DeadlineService deadlines;

    private final JsonMapper json = JsonMapper.builder().build();
    private MvcTestResult last;
    private JsonNode registration;
    private String station = AbstractIntegrationTest.FORUM;
    private String pickupCode;
    private String sessionToken;
    private String expiredTrackingCode;
    private JsonNode newRegistration;
    private long freeBefore;

    @Before
    public void resetWorld() {
        AbstractIntegrationTest.resetDatabase(jdbc);
        clock.setFixed(AbstractIntegrationTest.NOW);
        hardware.reset();
        hardware.setAutoClose(true);
        outbox.clear();
        guard.resetAll();
        ((StubGeocoder) geocoder).setUnavailable(false);
    }

    @After
    public void restoreClock() {
        clock.useSystemTime();
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode body() {
        try {
            return json.readTree(last.getResponse().getContentAsString());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult post(String uri, String body) {
        last = mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : body).exchange();
        return last;
    }

    private MvcTestResult get(String uri) {
        last = mvc.get().uri(uri).exchange();
        return last;
    }

    private String trackingCode() {
        return registration.get("trackingCode").asString();
    }

    private JsonNode register(String size, String station, String compartment, int hours) {
        post("/api/v1/parcels", AbstractIntegrationTest.registerBody(station, size, compartment, hours));
        return body();
    }

    private String latestCode(String trackingCode, Notification.Type type) {
        return outbox.latest().stream()
                .filter(e -> e.notification().type() == type && trackingCode.equals(e.notification().trackingCode()))
                .map(e -> CODE.matcher(e.notification().body()))
                .filter(Matcher::find).map(m -> m.group(1)).findFirst().orElseThrow();
    }

    private String stateOf(String trackingCode) {
        get("/api/v1/parcels/" + trackingCode);
        return body().get("state").asString();
    }

    private long freeOf(String size) {
        get("/api/v1/stations/" + station);
        return body().get("station").get("free").get(size.toLowerCase()).asLong();
    }

    private void collect(JsonNode reg) {
        post("/api/v1/courier/collections", "{\"deliveryCode\":\"" + reg.get("deliveryCode").asString() + "\"}");
    }

    private void dropOff(JsonNode reg) {
        post("/api/v1/terminals/" + station + "/drop-offs", "{\"code\":\"" + reg.get("dropOffCode").asString() + "\"}");
        String token = body().get("sessionToken").asString();
        post("/api/v1/terminals/" + station + "/drop-offs/" + token + "/door-closed", null);
    }

    // ------------------------------------------------------------------ UC1

    @Given("the geocoding provider is unavailable")
    public void geocoderUnavailable() {
        ((StubGeocoder) geocoder).setUnavailable(true);
    }

    @When("I search for stations near {string}")
    public void search(String address) {
        last = mvc.get().uri("/api/v1/stations/nearby").param("address", address).exchange();
    }

    @When("I search for stations within {int} km of {string}")
    public void searchWithin(int km, String address) {
        last = mvc.get().uri("/api/v1/stations/nearby").param("address", address).param("radiusKm", String.valueOf(km))
                .exchange();
    }

    @Then("{int} stations are listed")
    public void stationsListed(int n) {
        assertThat(body().get("stations").size()).isEqualTo(n);
    }

    @Then("station {string} is not listed")
    public void notListed(String name) {
        body().get("stations").forEach(s -> assertThat(s.get("name").asString()).isNotEqualTo(name));
    }

    @Then("the first station listed is {string}")
    public void firstStation(String name) {
        assertThat(last.getResponse().getStatus()).isEqualTo(200);
        assertThat(body().get("stations").get(0).get("name").asString()).isEqualTo(name);
    }

    @Then("the stations are sorted by distance")
    public void sortedByDistance() {
        List<Double> d = new ArrayList<>();
        body().get("stations").forEach(s -> d.add(s.get("distanceKm").asDouble()));
        assertThat(d).isSorted().isNotEmpty();
    }

    @Then("every listed station shows its free compartments per size")
    public void freePerSize() {
        body().get("stations").forEach(s -> assertThat(s.get("free").has("s") && s.get("free").has("m") && s.get("free").has("l")).isTrue());
    }

    @When("I open the station {string}")
    public void openStation(String code) {
        get("/api/v1/stations/" + code);
    }

    @Then("I see {int} compartments")
    public void seeCompartments(int n) {
        assertThat(body().get("compartments").size()).isEqualTo(n);
    }

    @Then("compartment {string} is {string}")
    public void compartmentIs(String code, String availability) {
        get("/api/v1/stations/" + station);
        JsonNode found = null;
        for (JsonNode c : body().get("compartments")) {
            if (c.get("code").asString().equals(code)) {
                found = c;
            }
        }
        assertThat(found).isNotNull();
        assertThat(found.get("availability").asString()).isEqualTo(availability);
    }

    @Then("the search fails with status {int} and code {string}")
    public void searchFails(int status, String code) {
        rejected(status, code);
    }

    @Then("the list of all stations is still available")
    public void allStations() {
        get("/api/v1/stations");
        assertThat(body().size()).isEqualTo(5);
    }

    // ------------------------------------------------------------------ UC2

    @When("the partner registers a {string} parcel at station {string} for {int} hours")
    public void partnerRegisters(String size, String station, int hours) {
        this.station = station;
        registration = register(size, station, null, hours);
    }

    @Given("a {string} parcel registered at station {string}")
    public void parcelRegistered(String size, String station) {
        parcelRegisteredFor(size, station, 48);
    }

    @Given("a {string} parcel registered at station {string} for {int} hours")
    public void parcelRegisteredFor(String size, String station, int hours) {
        this.station = station;
        freeBefore = freeOf(size);
        registration = register(size, station, null, hours);
        assertThat(last.getResponse().getStatus()).isEqualTo(201);
    }

    @Given("all {string} compartments of station {string} are reserved")
    public void allReserved(String size, String station) {
        long free = freeOf(size);
        for (int i = 0; i < free; i++) {
            register(size, station, null, 24);
        }
        assertThat(freeOf(size)).isZero();
    }

    @Then("the parcel is {string}")
    public void parcelIs(String state) {
        assertThat(stateOf(trackingCode())).isEqualTo(state);
    }

    @Then("a {string} compartment is reserved for it")
    public void compartmentReserved(String size) {
        assertThat(registration.get("compartmentCode").asString()).startsWith(size + "-");
    }

    @Then("the partner receives a tracking code, a delivery code and a 6-digit drop-off code")
    public void partnerReceivesCodes() {
        assertThat(registration.get("trackingCode").asString()).matches("ORB-\\d{6}-\\d{4}");
        assertThat(registration.get("deliveryCode").asString()).startsWith("DLV-");
        assertThat(registration.get("dropOffCode").asString()).matches("\\d{6}");
    }

    @Then("the price is {double} EUR")
    public void price(double price) {
        assertThat(registration.get("price").asDouble()).isEqualTo(price);
        assertThat(registration.get("currency").asString()).isEqualTo("EUR");
    }

    @Then("the request is rejected with status {int} and code {string}")
    public void rejected(int status, String code) {
        assertThat(last.getResponse().getStatus()).isEqualTo(status);
        assertThat(body().get("code").asString()).isEqualTo(code);
    }

    // ------------------------------------------------------------------ UC3

    @Given("the courier collected the parcel")
    public void courierCollected() {
        collect(registration);
        assertThat(last.getResponse().getStatus()).isEqualTo(200);
    }

    @Given("the door of the reserved compartment will not open")
    public void doorWontOpen() {
        hardware.inject(new CompartmentRef(station, registration.get("compartmentCode").asString()),
                FakeLockerHardware.Fault.DOOR_WONT_OPEN);
    }

    @Given("the station controller is not responding")
    public void controllerDown() {
        hardware.setStationUnreachable(true);
    }

    @When("the courier enters the drop-off code at the terminal")
    public void enterDropOffCode() {
        post("/api/v1/terminals/" + station + "/drop-offs", "{\"code\":\"" + registration.get("dropOffCode").asString() + "\"}");
        if (last.getResponse().getStatus() == 200) {
            sessionToken = body().get("sessionToken").asString();
        }
    }

    @Then("the compartment opens")
    public void compartmentOpens() {
        assertThat(last.getResponse().getStatus()).isEqualTo(200);
        assertThat(body().get("outcome").asString()).isEqualTo("DOOR_OPENED");
    }

    @When("the courier closes the door")
    public void courierClosesDoor() {
        post("/api/v1/terminals/" + station + "/drop-offs/" + sessionToken + "/door-closed", null);
        assertThat(body().get("outcome").asString()).isEqualTo("DEPOSITED");
    }

    @Then("the recipient receives a 6-digit pickup code different from the drop-off code")
    public void recipientGetsPickupCode() {
        pickupCode = latestCode(trackingCode(), Notification.Type.PICKUP_CODE);
        assertThat(pickupCode).matches("\\d{6}").isNotEqualTo(registration.get("dropOffCode").asString());
        assertThat(outbox.latest().get(0).notification().toEmail()).isEqualTo("joao@example.com");
    }

    @Then("the terminal reports {string} with a replacement available")
    public void terminalReports(String outcome) {
        assertThat(body().get("outcome").asString()).isEqualTo(outcome);
        assertThat(body().get("replacementAvailable").asBoolean()).isTrue();
    }

    @Then("the faulty compartment is out of service")
    public void faultyOutOfService() {
        compartmentIs(registration.get("compartmentCode").asString(), "OUT_OF_SERVICE");
    }

    @Then("the operator is notified of the malfunction")
    public void operatorNotified() {
        get("/api/v1/operator/stations/" + station + "/incidents");
        assertThat(body().get(0).get("type").asString()).isEqualTo("DOOR_DID_NOT_OPEN");
        assertThat(outbox.latest()).anySatisfy(e -> assertThat(e.notification().type()).isEqualTo(Notification.Type.OPERATOR_ALERT));
    }

    @When("the courier asks for another compartment")
    public void askReplacement() {
        post("/api/v1/terminals/" + station + "/drop-offs/" + sessionToken + "/replacement", null);
        assertThat(body().get("outcome").asString()).isEqualTo("REPLACEMENT_ISSUED");
    }

    @Then("the parcel is moved to another {string} compartment")
    public void movedTo(String size) {
        String now = body().get("compartmentCode").asString();
        assertThat(now).startsWith(size + "-").isNotEqualTo(registration.get("compartmentCode").asString());
    }

    @Then("the sender receives a new drop-off code that opens the new compartment")
    public void newCodeOpens() {
        String code = latestCode(trackingCode(), Notification.Type.REPLACEMENT_DROP_OFF_CODE);
        post("/api/v1/terminals/" + station + "/drop-offs", "{\"code\":\"" + code + "\"}");
        assertThat(body().get("outcome").asString()).isEqualTo("DOOR_OPENED");
    }

    // ------------------------------------------------------------------ UC4

    @Given("a {string} parcel waiting for pickup at station {string}")
    public void parcelWaiting(String size, String station) {
        parcelRegistered(size, station);
        collect(registration);
        dropOff(registration);
        pickupCode = latestCode(trackingCode(), Notification.Type.PICKUP_CODE);
    }

    @When("the recipient enters the pickup code at the terminal")
    public void enterPickupCode() {
        post("/api/v1/terminals/" + station + "/pickups", "{\"code\":\"" + pickupCode + "\"}");
    }

    @Given("the recipient already picked up the parcel")
    public void alreadyPickedUp() {
        enterPickupCode();
        assertThat(body().get("state").asString()).isEqualTo("PICKED_UP");
    }

    @Then("the compartment is free again")
    public void compartmentFreeAgain() {
        assertThat(freeOf(registration.get("size").asString())).isEqualTo(freeBefore);
    }

    @When("someone enters {int} wrong pickup codes at the terminal")
    public void wrongCodes(int n) {
        for (int i = 0; i < n; i++) {
            String wrong = String.format("%06d", (Integer.parseInt(pickupCode) + 1 + i) % 1_000_000);
            post("/api/v1/terminals/" + station + "/pickups", "{\"code\":\"" + wrong + "\"}");
        }
    }

    @Then("the terminal blocks code entry with status {int}")
    public void blocked(int status) {
        assertThat(last.getResponse().getStatus()).isEqualTo(status);
    }

    @Then("even the correct pickup code is rejected")
    public void correctRejected() {
        enterPickupCode();
        assertThat(last.getResponse().getStatus()).isEqualTo(429);
    }

    @Then("the recipient can pick up the parcel with the pickup code")
    public void canPickUp() {
        enterPickupCode();
        assertThat(last.getResponse().getStatus()).isEqualTo(200);
        assertThat(body().get("state").asString()).isEqualTo("PICKED_UP");
    }

    // ------------------------------------------------------------------ UC5

    @When("I track the parcel")
    public void track() {
        get("/api/v1/parcels/" + trackingCode());
    }

    @When("I track the parcel {string}")
    public void trackCode(String code) {
        get("/api/v1/parcels/" + code);
    }

    @Then("the current state is {string}")
    public void currentState(String state) {
        assertThat(body().get("state").asString()).isEqualTo(state);
    }

    @Then("the history is:")
    public void historyIs(DataTable table) {
        List<Map<String, String>> expected = table.asMaps();
        JsonNode history = body().get("history");
        assertThat(history.size()).isEqualTo(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertThat(history.get(i).get("to").asString()).isEqualTo(expected.get(i).get("to"));
            assertThat(history.get(i).get("actor").asString()).isEqualTo(expected.get(i).get("actor"));
            assertThat(history.get(i).get("at").asString()).isNotBlank();
        }
    }

    // ------------------------------------------------------------------ time

    @When("{int} hour(s) pass(es)")
    public void hoursPass(int hours) {
        clock.advance(Duration.ofHours(hours));
        deadlines.processDeadlines();
    }

    @When("{int} day(s) pass(es)")
    public void daysPass(int days) {
        clock.advance(Duration.ofDays(days));
        deadlines.processDeadlines();
    }

    @Then("the last transition was made by {string}")
    public void lastActor(String actor) {
        get("/api/v1/parcels/" + trackingCode());
        JsonNode history = body().get("history");
        assertThat(history.get(history.size() - 1).get("actor").asString()).isEqualTo(actor);
    }

    @When("another parcel is registered for the same compartment")
    public void anotherForSameCompartment() {
        expiredTrackingCode = trackingCode();
        newRegistration = register(registration.get("size").asString(), station,
                registration.get("compartmentCode").asString(), 24);
        assertThat(last.getResponse().getStatus()).isEqualTo(201);
    }

    @Then("the expired parcel is {string}")
    public void expiredParcelIs(String state) {
        assertThat(stateOf(expiredTrackingCode)).isEqualTo(state);
    }

    @When("the new parcel is dropped off in that compartment")
    public void newParcelDroppedOff() {
        collect(newRegistration);
        dropOff(newRegistration);
        assertThat(body().get("outcome").asString()).isEqualTo("DEPOSITED");
    }

    // ------------------------------------------------------------------ UC6

    @When("the operator lists the {string} parcels of station {string}")
    public void operatorLists(String state, String station) {
        get("/api/v1/operator/stations/" + station + "/parcels?state=" + state);
    }

    @Then("the parcel is in the list")
    public void parcelInList() {
        List<String> codes = new ArrayList<>();
        body().forEach(p -> codes.add(p.get("trackingCode").asString()));
        assertThat(codes).contains(trackingCode());
    }

    @When("the operator registers the removal of the parcel")
    public void operatorRemoval() {
        post("/api/v1/operator/parcels/" + trackingCode() + "/removal", null);
        assertThat(last.getResponse().getStatus()).isEqualTo(200);
    }

    @When("the operator puts compartment {string} of station {string} out of service")
    public void operatorOutOfService(String compartment, String station) {
        this.station = station;
        last = mvc.put().uri("/api/v1/operator/stations/" + station + "/compartments/" + compartment + "/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OUT_OF_SERVICE\",\"reason\":\"Faulty lock\"}")
                .exchange();
        assertThat(last.getResponse().getStatus()).isEqualTo(204);
    }

    @Then("no parcel can be registered in compartment {string} of station {string}")
    public void cannotRegisterIn(String compartment, String station) {
        register(compartment.substring(0, 1), station, compartment, 24);
        assertThat(last.getResponse().getStatus()).isEqualTo(409);
    }
}
