package pt.ua.deti.tqs.orbit.support;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import pt.ua.deti.tqs.orbit.adapter.geocoding.StubGeocoder;
import pt.ua.deti.tqs.orbit.adapter.hardware.FakeLockerHardware;
import pt.ua.deti.tqs.orbit.adapter.notification.NotificationOutbox;
import pt.ua.deti.tqs.orbit.config.MutableClock;
import pt.ua.deti.tqs.orbit.port.GeocoderPort;
import pt.ua.deti.tqs.orbit.service.AttemptGuard;

/**
 * Full application context on a random port, PostgreSQL in Testcontainers, and the in-process fakes (hardware,
 * geocoder, clock, notification outbox) reachable for arranging state and injecting failures. One shared context
 * (and one container) for every integration, functional and acceptance test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
public abstract class AbstractIntegrationTest {

    public static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");
    public static final String FORUM = "ORB-AVR-001";

    @LocalServerPort
    protected int port;
    @Autowired
    protected MockMvcTester mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected FakeLockerHardware hardware;
    @Autowired
    protected NotificationOutbox outbox;
    @Autowired
    protected AttemptGuard guard;
    @Autowired
    protected GeocoderPort geocoder;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void resetWorld() {
        resetDatabase(jdbc);
        clock.setFixed(NOW);
        hardware.reset();
        hardware.setAutoClose(true);
        outbox.clear();
        guard.resetAll();
        ((StubGeocoder) geocoder).setUnavailable(false);
    }

    @AfterEach
    void backToSystemTime() {
        clock.useSystemTime();
    }

    /** Removes all parcels and restores the seed compartment statuses. */
    public static void resetDatabase(JdbcTemplate jdbc) {
        jdbc.execute("DELETE FROM parcel_transition; DELETE FROM location_scan; DELETE FROM incident; DELETE FROM parcel;");
        jdbc.execute("UPDATE compartment SET status = 'IN_SERVICE', status_reason = NULL");
        jdbc.execute("UPDATE compartment SET status = 'OUT_OF_SERVICE', status_reason = 'Door sensor maintenance' "
                + "WHERE code IN ('S-06', 'L-12') AND station_id = (SELECT id FROM station WHERE code = 'ORB-AVR-001')");
    }

    // ------------------------------------------------------------------ API helpers

    protected JsonNode json(MvcTestResult result) {
        try {
            return json.readTree(result.getResponse().getContentAsString());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    protected MvcTestResult post(String uri, String body) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : body).exchange();
    }

    protected MvcTestResult get(String uri) {
        return mvc.get().uri(uri).exchange();
    }

    public static String registerBody(String station, String size, String compartment, int hours) {
        return """
                {"stationCode":"%s","size":"%s",%s"leaseHours":%d,
                 "sender":{"name":"Ana Silva","email":"ana@example.com","phone":"+351 912 345 678"},
                 "recipient":{"name":"João Santos","email":"joao@example.com","phone":"+351 934 567 890"}}"""
                .formatted(station, size, compartment == null ? "" : "\"compartmentCode\":\"" + compartment + "\",", hours);
    }

    /** Registers a parcel; returns the registration JSON (trackingCode, deliveryCode, dropOffCode...). */
    protected JsonNode register(String station, String size, String compartment, int hours) {
        MvcTestResult r = post("/api/v1/parcels", registerBody(station, size, compartment, hours));
        if (r.getResponse().getStatus() != 201) {
            throw new IllegalStateException("registration failed: " + r.getResponse().getStatus());
        }
        return json(r);
    }

    protected void collect(JsonNode registration) {
        post("/api/v1/courier/collections", "{\"deliveryCode\":\"" + registration.get("deliveryCode").asString() + "\"}");
    }

    /** Collect + drop-off through the terminal; returns the pickup PIN read from the notification outbox. */
    protected String deliverToLocker(String station, JsonNode registration) {
        collect(registration);
        JsonNode opened = json(post("/api/v1/terminals/" + station + "/drop-offs",
                "{\"code\":\"" + registration.get("dropOffCode").asString() + "\"}"));
        post("/api/v1/terminals/" + station + "/drop-offs/" + opened.get("sessionToken").asString() + "/door-closed", null);
        return pickupCode(registration.get("trackingCode").asString());
    }

    protected String pickupCode(String trackingCode) {
        return outbox.latest().stream()
                .filter(e -> trackingCode.equals(e.notification().trackingCode()))
                .map(e -> e.notification().body())
                .map(b -> java.util.regex.Pattern.compile("[Cc]ode: (\\d{6})").matcher(b))
                .filter(java.util.regex.Matcher::find)
                .map(m -> m.group(1))
                .findFirst().orElseThrow();
    }

    protected String state(String trackingCode) {
        return json(get("/api/v1/parcels/" + trackingCode)).get("state").asString();
    }
}
