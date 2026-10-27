package pt.ua.deti.tqs.orbit.web.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.GeoPoint;
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.port.GeocodingUnavailableException;
import pt.ua.deti.tqs.orbit.service.StationService;
import pt.ua.deti.tqs.orbit.service.view.NearbySearchResult;
import pt.ua.deti.tqs.orbit.service.view.SizeCounts;
import pt.ua.deti.tqs.orbit.service.view.StationSummary;

/**
 * <b>C</b> — the station controller, in a web "slice".
 *
 * <p>{@code @WebMvcTest} starts only the web layer (Spring MVC infrastructure, JSON conversion, the controller named
 * here and the {@code @RestControllerAdvice} {@link ApiExceptionHandler}). No server, no database, no services, no
 * geocoder. The controller's {@link StationService} is replaced in the context by a Mockito mock with
 * {@code @MockitoBean}, and requests are sent through {@link MockMvc}, which calls the {@code DispatcherServlet}
 * directly — no HTTP connection is involved. The ranking rules are tested in {@code StationRankingTest}; here the
 * oracle is the HTTP contract (status codes, JSON body, RFC 9457 errors).</p>
 */
@WebMvcTest(StationApiController.class)
@EnableConfigurationProperties(OrbitProperties.class)
@DisplayName("StationApiController (web slice, service mocked)")
class StationApiControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StationService service;

    private static final String ADDRESS = "Rua de Coimbra 10, Aveiro";

    private static StationSummary station(String code, String name, Double distanceKm) {
        return new StationSummary(code, name, "Rua 1", "3810-001", "Aveiro", 40.64, -8.65, null, 16,
                new SizeCounts(7, 4, 3), distanceKm, distanceKm == null ? null : 2);
    }

    @Test
    @DisplayName("US1: GET /stations lists every station with its free compartments per size")
    void whenGetStations_thenReturnJsonArray() throws Exception {
        when(service.listAll(null)).thenReturn(List.of(
                station("ORB-AVR-001", "Fórum Aveiro", null),
                station("ORB-AVR-002", "Estação de Aveiro", null)));

        mvc.perform(get("/api/v1/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].code", is("ORB-AVR-001")))
                .andExpect(jsonPath("$[0].free.m", is(4)))
                .andExpect(jsonPath("$[0].distanceKm", nullValue()))
                .andDo(print());

        verify(service).listAll(null);
    }

    @Test
    @DisplayName("US1: the chosen radius is passed to the service and returned")
    void whenGetNearbyWithRadius_thenPassTheRadiusToTheService() throws Exception {
        when(service.nearbyAddress(ADDRESS, null, 2.0)).thenReturn(new NearbySearchResult(ADDRESS, 40.6405, -8.6538,
                2.0, List.of(station("ORB-AVR-001", "Fórum Aveiro", 0.1))));

        mvc.perform(get("/api/v1/stations/nearby").param("address", ADDRESS).param("radiusKm", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.radiusKm", is(2.0)))
                .andExpect(jsonPath("$.stations[0].name", is("Fórum Aveiro")))
                .andExpect(jsonPath("$.stations[0].distanceKm", is(0.1)))
                .andDo(print());

        verify(service).nearbyAddress(ADDRESS, null, 2.0);
    }

    @Test
    @DisplayName("US1: a size filter that is not S, M or L is a 400 and the service is never called")
    void whenSizeIsNotAValidSize_thenReturnError400() throws Exception {

        mvc.perform(get("/api/v1/stations/nearby").param("address", ADDRESS).param("size", "XL"))
                .andExpect(status().isBadRequest())
                .andDo(print());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("US1: an unknown address is a 404 ADDRESS_NOT_FOUND")
    void whenAddressIsUnknown_thenReturnError404() throws Exception {
        when(service.nearbyAddress("Nowhere 999", null, null))
                .thenThrow(OrbitException.notFound("ADDRESS_NOT_FOUND", "We could not find that address"));

        mvc.perform(get("/api/v1/stations/nearby").param("address", "Nowhere 999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ADDRESS_NOT_FOUND")))
                .andExpect(jsonPath("$.detail", is("We could not find that address")))
                .andDo(print());
    }

    @Test
    @DisplayName("NFR4: the geocoder down is a 503 with Retry-After and the fallback URL")
    void whenGeocoderIsUnavailable_thenReturnError503WithFallback() throws Exception {
        when(service.nearbyAddress(any(), any(), any())).thenThrow(
                new GeocodingUnavailableException(GeocodingUnavailableException.Reason.TIMEOUT, "slow"));

        mvc.perform(get("/api/v1/stations/nearby").param("address", ADDRESS))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code", is("GEOCODER_UNAVAILABLE")))
                .andExpect(jsonPath("$.fallback", is("/api/v1/stations")))
                .andDo(print());
    }

    @Test
    @DisplayName("US1: coordinates out of range are a 422 INVALID_COORDINATES")
    void whenCoordinatesAreOutOfRange_thenReturnError422() throws Exception {
        when(service.nearbyPoint(any(GeoPoint.class), any(), any()))
                .thenThrow(new IllegalArgumentException("latitude out of range: 99.0"));

        mvc.perform(get("/api/v1/stations/nearby").param("lat", "99").param("lon", "-8.65"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", is("INVALID_COORDINATES")))
                .andDo(print());
    }

    @Test
    @DisplayName("US1: a radius above the maximum is a 422 INVALID_RADIUS")
    void whenRadiusIsTooLarge_thenReturnError422() throws Exception {
        when(service.nearbyAddress(ADDRESS, null, 500.0)).thenThrow(OrbitException.unprocessable("INVALID_RADIUS",
                "The radius must be between 0 and 20 km"));

        mvc.perform(get("/api/v1/stations/nearby").param("address", ADDRESS).param("radiusKm", "500"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", is("INVALID_RADIUS")))
                .andDo(print());
    }

    @Test
    @DisplayName("US1: an unknown station is a 404")
    void whenGetUnknownStation_thenReturnError404() throws Exception {
        when(service.detail("NOPE")).thenThrow(OrbitException.notFound("STATION_NOT_FOUND", "Station NOPE not found"));

        mvc.perform(get("/api/v1/stations/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("STATION_NOT_FOUND")))
                .andDo(print());

        verify(service).detail("NOPE");
    }

    @Test
    @DisplayName("US1: the size filter is passed to the service")
    void whenGetStationsWithSize_thenPassTheSizeToTheService() throws Exception {
        when(service.listAll(CompartmentSize.L)).thenReturn(List.of());

        mvc.perform(get("/api/v1/stations").param("size", "L"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)))
                .andDo(print());

        verify(service).listAll(CompartmentSize.L);
    }
}
