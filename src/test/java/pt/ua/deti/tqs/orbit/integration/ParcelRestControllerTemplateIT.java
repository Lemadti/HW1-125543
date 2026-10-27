package pt.ua.deti.tqs.orbit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;
import pt.ua.deti.tqs.orbit.support.AbstractIntegrationTest;

/**
 * <b>E</b> — the whole application, on a real server, with a real HTTP client and a real PostgreSQL.
 *
 * <ul>
 *   <li>{@code webEnvironment = RANDOM_PORT} starts the embedded Tomcat on a free port.</li>
 *   <li>{@code @AutoConfigureTestRestTemplate} provides a {@link TestRestTemplate} already pointing at that port (in
 *       Spring Boot 4 it is no longer provided by default).</li>
 *   <li>{@code @Testcontainers} + {@code @Container} start a PostgreSQL container before the tests of this class and
 *       stop it afterwards; {@code @ServiceConnection} makes Spring Boot connect the application to it — no URL, user
 *       or password to configure. Flyway creates the schema and the seed stations, as in production.</li>
 * </ul>
 * {@code @DirtiesContext} closes this class's own context when it ends: the container is stopped then, and the
 * scheduled deadline job of a cached context would otherwise keep trying to reach it.
 * <p>Unlike MockMvc, every request here goes through a TCP connection, the servlet container and the JSON
 * (de)serialisation a real client would see. Requires Docker. Run with {@code mvn verify} (Failsafe picks up the
 * {@code *IT} classes).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext
@DisplayName("Parcel REST API — real server, real HTTP client, real PostgreSQL")
class ParcelRestControllerTemplateIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private TestRestTemplate restClient;

    @Autowired
    private ParcelRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void resetDb() {
        AbstractIntegrationTest.resetDatabase(jdbc);
    }

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    @DisplayName("US2: a valid registration answers 201 with the codes and is stored as CREATED")
    void whenValidInput_thenRegisterParcel() {
        ResponseEntity<RegistrationResult> response = restClient.postForEntity("/api/v1/parcels",
                json(AbstractIntegrationTest.registerBody("ORB-AVR-001", "M", "M-07")), RegistrationResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasPath("/api/v1/parcels/" + response.getBody().trackingCode());
        assertThat(response.getBody().compartmentCode()).isEqualTo("M-07");
        assertThat(response.getBody().dropOffCode()).matches("\\d{6}");
        assertThat(repository.findAll()).singleElement()
                .satisfies(p -> assertThat(p.getState()).isEqualTo(ParcelState.CREATED));
    }

    @Test
    @DisplayName("US2: an invalid body answers 400 problem+json and nothing is stored")
    void whenInvalidInput_thenBadRequestAndNothingStored() {
        ResponseEntity<Map<String, Object>> response = restClient.exchange("/api/v1/parcels", HttpMethod.POST,
                json("{\"stationCode\":\"ORB-AVR-001\"}"), new ParameterizedTypeReference<Map<String, Object>>() { });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_FAILED");
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("US1: GET /stations lists the five seeded Aveiro stations")
    void givenSeededStations_whenGetStations_thenStatus200() {
        ResponseEntity<List<Map<String, Object>>> response = restClient.exchange("/api/v1/stations", HttpMethod.GET,
                null, new ParameterizedTypeReference<List<Map<String, Object>>>() { });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(s -> s.get("code"))
                .containsExactlyInAnyOrder("ORB-AVR-001", "ORB-AVR-002", "ORB-AVR-003", "ORB-AVR-004", "ORB-AVR-005");
    }

    @Test
    @DisplayName("US5: an unknown tracking code answers 404 PARCEL_NOT_FOUND")
    void whenTrackUnknownParcel_thenNotFound() {
        ResponseEntity<Map<String, Object>> response = restClient.exchange("/api/v1/parcels/ORB-000000-0000",
                HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() { });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "PARCEL_NOT_FOUND");
    }

    @Test
    @DisplayName("the database is a real PostgreSQL, started by Testcontainers")
    void theDatabaseIsARealPostgreSQL() {
        assertThat(postgres.isRunning()).isTrue();
        assertThat(postgres.getJdbcUrl()).startsWith("jdbc:postgresql://");
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL 17");
    }
}
