package pt.ua.deti.tqs.orbit.support;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pt.ua.deti.tqs.orbit.adapter.geocoding.StubGeocoder;
import pt.ua.deti.tqs.orbit.adapter.hardware.FakeLockerHardware;
import pt.ua.deti.tqs.orbit.adapter.notification.NotificationOutbox;
import pt.ua.deti.tqs.orbit.config.MutableClock;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.port.GeocoderPort;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;
import pt.ua.deti.tqs.orbit.service.AttemptGuard;
import pt.ua.deti.tqs.orbit.service.ParcelService;
import pt.ua.deti.tqs.orbit.service.RegisterParcelCommand;
import pt.ua.deti.tqs.orbit.service.TerminalService;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;
import pt.ua.deti.tqs.orbit.service.view.TerminalResult;

/**
 * <b>D</b> — the whole application, with a real PostgreSQL, for the integration and browser tests.
 *
 * <ul>
 *   <li>{@code webEnvironment = RANDOM_PORT} starts the embedded Tomcat on a free port (the browser tests and
 *       {@link TestRestTemplate} need a real server); {@code @AutoConfigureMockMvc} also provides {@link MockMvc}, which
 *       calls the {@code DispatcherServlet} directly.</li>
 *   <li>{@link PostgresTestConfig} declares the PostgreSQL container as a bean with {@code @ServiceConnection}: Spring
 *       Boot starts it and connects the application to it. It is a bean (not a {@code @Container} field per class, as
 *       in Lab 03) so that every subclass shares ONE context and ONE container: a per-class container would be stopped
 *       after each class while the cached context still pointed at it.</li>
 *   <li>Nothing is mocked: controller → service → repository → database. Only the edges of the system are in-process
 *       fakes that the tests control: the locker hardware, the geocoder, the clock and the notification outbox.</li>
 * </ul>
 *
 * <p>Front door / back door: what is under test is acted on through the front door (MockMvc, HTTP or the browser).
 * Arranging data ("a parcel waiting for pickup") goes through the <b>back door</b> — the services, because the PINs
 * only exist in clear when they are generated — and checking what was stored goes through the repositories. Every test
 * starts from a known state: the seed data, the clock at {@link #NOW} and no injected faults.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
public abstract class AbstractIntegrationTest {

    /** Saturday 24 October 2026, 09:00 in Lisbon. */
    public static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");
    public static final String FORUM = "ORB-AVR-001";

    private static final Pattern CODE = Pattern.compile("[Cc]ode: (\\d{6})");

    @LocalServerPort
    protected int port;
    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected TestRestTemplate restClient;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected ParcelRepository parcels;
    @Autowired
    protected ParcelService parcelService;
    @Autowired
    protected TerminalService terminalService;
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

    /** A registration request body, as a partner would send it. */
    public static String registerBody(String station, String size, String compartment) {
        return """
                {"stationCode":"%s","size":"%s",%s
                 "sender":{"name":"Ana Silva","email":"ana@example.com","phone":"+351 912 345 678"},
                 "recipient":{"name":"João Santos","email":"joao@example.com","phone":"+351 934 567 890"}}"""
                .formatted(station, size, compartment == null ? "" : "\"compartmentCode\":\"" + compartment + "\",");
    }

    // ------------------------------------------------------------------ back door: arrange

    /** Registers a parcel (the partner's step) and returns the registration, with the drop-off PIN in clear. */
    protected RegistrationResult register(String station, String size, String compartment) {
        return parcelService.register(new RegisterParcelCommand(station, CompartmentSize.valueOf(size), compartment,
                TestData.SENDER, TestData.RECIPIENT));
    }

    /** The courier scans the delivery code: the parcel is ON_ITS_WAY. */
    protected void collect(RegistrationResult registration) {
        parcelService.collect(registration.deliveryCode(), null);
    }

    /** Collect, drop off at the terminal and close the door; returns the pickup PIN sent to the recipient. */
    protected String deliverToLocker(String station, RegistrationResult registration) {
        collect(registration);
        TerminalResult opened = terminalService.startDropOff(station, registration.dropOffCode());
        terminalService.confirmDropOff(station, opened.sessionToken());
        return pickupCode(registration.trackingCode());
    }

    /** The latest 6-digit code sent about the parcel (pickup or replacement drop-off code), read from the outbox. */
    protected String pickupCode(String trackingCode) {
        return outbox.latest().stream()
                .filter(e -> trackingCode.equals(e.notification().trackingCode()))
                .map(e -> CODE.matcher(e.notification().body()))
                .filter(Matcher::find)
                .map(m -> m.group(1))
                .findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ back door: check what was stored

    /** The state stored in the database. */
    protected ParcelState stateOf(String trackingCode) {
        return parcels.findByTrackingCode(trackingCode).orElseThrow().getState();
    }

    /** The compartment the database holds the parcel in. */
    protected String compartmentOf(String trackingCode) {
        return jdbc.queryForObject("SELECT c.code FROM parcel p JOIN compartment c ON c.id = p.compartment_id "
                + "WHERE p.tracking_code = ?", String.class, trackingCode);
    }

    /** The actor of the last transition stored for the parcel. */
    protected String lastActorOf(String trackingCode) {
        return jdbc.queryForObject("SELECT t.actor FROM parcel_transition t JOIN parcel p ON p.id = t.parcel_id "
                + "WHERE p.tracking_code = ? ORDER BY t.id DESC LIMIT 1", String.class, trackingCode);
    }
}
