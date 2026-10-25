package pt.ua.deti.tqs.orbit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.InvalidTransitionException;
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.port.GeocodingUnavailableException;
import pt.ua.deti.tqs.orbit.service.ParcelService;
import pt.ua.deti.tqs.orbit.service.StationService;
import pt.ua.deti.tqs.orbit.service.TerminalService;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;
import pt.ua.deti.tqs.orbit.web.api.ParcelApiController;
import pt.ua.deti.tqs.orbit.web.api.StationApiController;
import pt.ua.deti.tqs.orbit.web.api.TerminalApiController;

/**
 * Web-layer slice: request validation, status codes and the RFC 9457 error format, with the services mocked
 * (the business rules are tested elsewhere; here the oracle is the HTTP contract).
 */
@WebMvcTest({ParcelApiController.class, StationApiController.class, TerminalApiController.class})
@EnableConfigurationProperties(OrbitProperties.class)
class ApiContractWebMvcTest {

    private static final String VALID_BODY = """
            {"stationCode":"ORB-AVR-001","size":"M","leaseHours":48,
             "sender":{"name":"Ana Silva","email":"ana@example.com","phone":"+351 912 345 678"},
             "recipient":{"name":"João Santos","email":"joao@example.com","phone":"+351 934 567 890"}}""";

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    ParcelService parcels;
    @MockitoBean
    StationService stations;
    @MockitoBean
    TerminalService terminal;

    @Test
    void registerReturns201WithLocation() {
        when(parcels.register(any())).thenReturn(new RegistrationResult("ORB-261024-0714", ParcelState.CREATED,
                "ORB-AVR-001", "Fórum Aveiro", "M-07", CompartmentSize.M, null, 48, null, new BigDecimal("24.00"), "EUR",
                "DLV-AAAA", "123456"));

        assertThat(mvc.post().uri("/api/v1/parcels").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "/api/v1/parcels/ORB-261024-0714")
                .bodyJson().extractingPath("$.dropOffCode").isEqualTo("123456");
    }

    @Test
    void invalidBodyIs400WithFieldErrors() {
        String body = VALID_BODY.replace("ana@example.com", "not-an-email").replace("\"size\":\"M\",", "");
        assertThat(mvc.post().uri("/api/v1/parcels").contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("VALIDATION_FAILED"))
                .hasPathSatisfying("$.errors['sender.email']", v -> v.assertThat().isNotNull())
                .hasPathSatisfying("$.errors.size", v -> v.assertThat().isNotNull());
    }

    @Test
    void unknownEnumIsMalformedRequest() {
        assertThat(mvc.post().uri("/api/v1/parcels").contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY.replace("\"M\"", "\"XL\"")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void stationFullIs409() {
        when(parcels.register(any())).thenThrow(OrbitException.conflict("STATION_FULL", "No medium compartment"));
        assertThat(mvc.post().uri("/api/v1/parcels").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("STATION_FULL");
    }

    @Test
    void invalidTransitionIs409WithCurrentStateAndAllowedEvents() {
        when(parcels.cancel(eq("ORB-1"), eq(Actor.PARTNER), isNull()))
                .thenThrow(new InvalidTransitionException(ParcelState.READY_FOR_PICKUP, ParcelEvent.CANCEL, Actor.PARTNER, "no"));
        assertThat(mvc.post().uri("/api/v1/parcels/ORB-1/cancel"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("INVALID_TRANSITION"))
                .hasPathSatisfying("$.currentState", v -> v.assertThat().isEqualTo("READY_FOR_PICKUP"))
                .hasPathSatisfying("$.allowedEvents", v -> v.assertThat().asArray().contains("PICK_UP", "PICKUP_DEADLINE_PASSED"));
    }

    @Test
    void onlyPartnerOrRecipientMayCancel() {
        assertThat(mvc.post().uri("/api/v1/parcels/ORB-1/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"actor\":\"COURIER\"}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_ACTOR");
    }

    @Test
    void unknownParcelIs404() {
        when(parcels.track("ORB-X")).thenThrow(OrbitException.notFound("PARCEL_NOT_FOUND", "not found"));
        assertThat(mvc.get().uri("/api/v1/parcels/ORB-X")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("PARCEL_NOT_FOUND");
    }

    @Test
    void geocoderDownIs503WithFallbackHint() {
        when(stations.nearbyAddress(anyString(), any(), any())).thenThrow(
                new GeocodingUnavailableException(GeocodingUnavailableException.Reason.TIMEOUT, "slow"));
        assertThat(mvc.get().uri("/api/v1/stations/nearby?address=Chmielna"))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .hasHeader("Retry-After", "30")
                .bodyJson()
                .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("GEOCODER_UNAVAILABLE"))
                .hasPathSatisfying("$.fallback", v -> v.assertThat().isEqualTo("/api/v1/stations"));
    }

    @Test
    void invalidCoordinatesAre422() {
        when(stations.nearbyPoint(any(), any(), any())).thenThrow(new IllegalArgumentException("latitude out of range: 99.0"));
        assertThat(mvc.get().uri("/api/v1/stations/nearby?lat=99&lon=21"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void chosenRadiusIsPassedToTheService() {
        when(stations.nearbyAddress("Rua de Coimbra 10, Aveiro", null, 2.0)).thenReturn(
                new pt.ua.deti.tqs.orbit.service.view.NearbySearchResult("Rua de Coimbra 10, Aveiro", 40.64, -8.65, 2.0,
                        java.util.List.of()));
        assertThat(mvc.get().uri("/api/v1/stations/nearby").param("address", "Rua de Coimbra 10, Aveiro")
                .param("radiusKm", "2"))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.radiusKm").isEqualTo(2.0);
    }

    @Test
    void lockedTerminalIs429WithRetryAfter() {
        when(terminal.startPickup("S", "000000")).thenThrow(new OrbitException(OrbitException.Kind.LOCKED,
                "TOO_MANY_ATTEMPTS", "blocked", Map.of("lockedUntil", java.time.Instant.now().plusSeconds(3600).toString())));
        assertThat(mvc.post().uri("/api/v1/terminals/S/pickups").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"000000\"}"))
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .containsHeader("Retry-After")
                .bodyJson().extractingPath("$.code").isEqualTo("TOO_MANY_ATTEMPTS");
    }

    @Test
    void blankCodeIs400() {
        assertThat(mvc.post().uri("/api/v1/terminals/S/drop-offs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
