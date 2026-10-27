package pt.ua.deti.tqs.orbit.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.InvalidTransitionException;
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.service.ParcelService;
import pt.ua.deti.tqs.orbit.service.RegisterParcelCommand;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;
import pt.ua.deti.tqs.orbit.service.view.TrackingView;

/**
 * <b>C</b> — the parcel controller, in a web "slice".
 *
 * <p>{@code @WebMvcTest} starts only the web layer (Spring MVC infrastructure, JSON conversion, the controller named
 * here and the {@code @RestControllerAdvice} {@link ApiExceptionHandler}). No server, no database, no services. The
 * controller's {@link ParcelService} is replaced in the context by a Mockito mock with {@code @MockitoBean}, and
 * requests are sent through {@link MockMvc}, which calls the {@code DispatcherServlet} directly — no HTTP connection
 * is involved.</p>
 */
@WebMvcTest(ParcelApiController.class)
@EnableConfigurationProperties(OrbitProperties.class)
@DisplayName("ParcelApiController (web slice, service mocked)")
class ParcelApiControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ParcelService service;

    private static final String TRACKING_CODE = "ORB-261024-0714";
    private static final Instant CREATED_AT = Instant.parse("2026-10-24T08:00:00Z");
    private static final Instant DROP_OFF_DEADLINE = Instant.parse("2026-11-06T09:00:00Z");
    private static final String VALID_BODY = """
            {"stationCode": "ORB-AVR-001", "size": "M",
             "sender": {"name": "Ana Silva", "email": "ana@example.com", "phone": "+351 912 345 678"},
             "recipient": {"name": "João Santos", "email": "joao@example.com", "phone": "+351 934 567 890"}}""";

    private static TrackingView tracking(ParcelState state, ParcelEvent... allowedEvents) {
        return new TrackingView(TRACKING_CODE, state, "ORB-AVR-001", "Fórum Aveiro", "Rua Batalhão Caçadores, Aveiro",
                40.64, -8.65, "M-07", CompartmentSize.M, "João S.", CREATED_AT, DROP_OFF_DEADLINE, null, null, null,
                List.of(), List.of(), List.of(allowedEvents));
    }

    @Test
    @DisplayName("US2: POST /parcels registers and answers 201 with the Location header")
    void whenPostRequest_thenRegisterAndReturnCreatedWithLocation() throws Exception {
        when(service.register(any())).thenReturn(new RegistrationResult(TRACKING_CODE, ParcelState.CREATED,
                "ORB-AVR-001", "Fórum Aveiro", "M-07", CompartmentSize.M, DROP_OFF_DEADLINE, new BigDecimal("3.00"),
                "EUR", "DLV-AAAA", "123456"));

        mvc.perform(post("/api/v1/parcels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/parcels/" + TRACKING_CODE))
                .andExpect(jsonPath("$.trackingCode", is(TRACKING_CODE)))
                .andExpect(jsonPath("$.state", is("CREATED")))
                .andExpect(jsonPath("$.compartmentCode", is("M-07")))
                .andExpect(jsonPath("$.dropOffDeadline", is("2026-11-06T09:00:00Z")))
                .andExpect(jsonPath("$.price", is(3.00)))
                .andExpect(jsonPath("$.deliveryCode", is("DLV-AAAA")))
                .andExpect(jsonPath("$.dropOffCode", is("123456")))
                .andDo(print());

        // Contact has no equals(), so the command is captured and checked field by field
        ArgumentCaptor<RegisterParcelCommand> command = ArgumentCaptor.forClass(RegisterParcelCommand.class);
        verify(service).register(command.capture());
        assertThat(command.getValue().stationCode()).isEqualTo("ORB-AVR-001");
        assertThat(command.getValue().size()).isEqualTo(CompartmentSize.M);
        assertThat(command.getValue().sender().getEmail()).isEqualTo("ana@example.com");
        assertThat(command.getValue().recipient().getName()).isEqualTo("João Santos");
    }

    @Test
    @DisplayName("US2: a blank station is a 400 and the service is never called")
    void whenPostRequestWithNoStationCode_thenReturnError400() throws Exception {

        mvc.perform(post("/api/v1/parcels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"ORB-AVR-001\"", "\" \"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andDo(print());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("US2: invalid fields are a 400 problem+json listing each field error")
    void whenPostRequestWithInvalidFields_thenReturnError400WithFieldErrors() throws Exception {
        String body = VALID_BODY.replace("ana@example.com", "not-an-email").replace("\"size\": \"M\",", "");

        mvc.perform(post("/api/v1/parcels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.errors['sender.email']", notNullValue()))
                .andExpect(jsonPath("$.errors.size", notNullValue()))
                .andDo(print());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("US2: a size that is not S, M or L is a 400 MALFORMED_REQUEST")
    void whenPostRequestWithInvalidSize_thenReturnError400() throws Exception {

        mvc.perform(post("/api/v1/parcels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"M\"", "\"XL\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("MALFORMED_REQUEST")))
                .andDo(print());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Compartment allocation: STATION_FULL is a 409 with the reason")
    void whenPostRequestRegisterThrowsStationFull_thenReturnConflict409WithDetailedReason() throws Exception {
        when(service.register(any()))
                .thenThrow(OrbitException.conflict("STATION_FULL", "No free medium compartment at this station"));

        mvc.perform(post("/api/v1/parcels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("STATION_FULL")))
                .andExpect(jsonPath("$.detail", is("No free medium compartment at this station")))
                .andDo(print());

        verify(service).register(any());
    }

    @Test
    @DisplayName("US5: GET /parcels/{code} returns state, masked name and allowed events")
    void whenGetRequest_thenReturnTrackingView() throws Exception {
        when(service.track(TRACKING_CODE)).thenReturn(tracking(ParcelState.CREATED, ParcelEvent.CANCEL,
                ParcelEvent.COLLECT, ParcelEvent.DROP_OFF_DEADLINE_PASSED));

        mvc.perform(get("/api/v1/parcels/" + TRACKING_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode", is(TRACKING_CODE)))
                .andExpect(jsonPath("$.state", is("CREATED")))
                .andExpect(jsonPath("$.recipientName", is("João S.")))
                .andExpect(jsonPath("$.allowedEvents", hasItems("CANCEL", "COLLECT")))
                .andDo(print());

        verify(service).track(TRACKING_CODE);
    }

    @Test
    @DisplayName("US5: an unknown tracking code is a 404")
    void whenGetRequestTrackThrowsParcelNotFound_thenReturnError404() throws Exception {
        String trackingCode = "NOPE";
        when(service.track(trackingCode))
                .thenThrow(OrbitException.notFound("PARCEL_NOT_FOUND", "Parcel NOPE not found"));

        mvc.perform(get("/api/v1/parcels/" + trackingCode))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("PARCEL_NOT_FOUND")))
                .andDo(print());

        verify(service).track(trackingCode);
    }

    @Test
    @DisplayName("State machine: cancel by the partner (default actor) returns the cancelled parcel")
    void whenPostRequestCancel_thenReturnCancelledParcel() throws Exception {
        when(service.cancel(TRACKING_CODE, Actor.PARTNER, null)).thenReturn(tracking(ParcelState.CANCELLED));

        mvc.perform(post("/api/v1/parcels/" + TRACKING_CODE + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("CANCELLED")))
                .andExpect(jsonPath("$.allowedEvents.length()", is(0)))
                .andDo(print());

        verify(service).cancel(TRACKING_CODE, Actor.PARTNER, null);
    }

    @Test
    @DisplayName("State machine: cancel by the recipient passes actor and reason")
    void whenPostRequestCancelByRecipientWithReason_thenPassBothToTheService() throws Exception {
        when(service.cancel(TRACKING_CODE, Actor.RECIPIENT, "No longer needed"))
                .thenReturn(tracking(ParcelState.CANCELLED));

        mvc.perform(post("/api/v1/parcels/" + TRACKING_CODE + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor": "RECIPIENT", "reason": "No longer needed"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("CANCELLED")))
                .andDo(print());

        verify(service).cancel(TRACKING_CODE, Actor.RECIPIENT, "No longer needed");
    }

    @Test
    @DisplayName("State machine: only partner or recipient may cancel (422)")
    void whenPostRequestCancelByCourier_thenReturnError422() throws Exception {

        mvc.perform(post("/api/v1/parcels/" + TRACKING_CODE + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor": "COURIER"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", is("INVALID_ACTOR")))
                .andDo(print());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("State machine: an invalid transition is a 409 with currentState and allowedEvents")
    void whenPostRequestCancelThrowsInvalidTransition_thenDecideFromApiExceptionHandler() throws Exception {
        when(service.cancel(TRACKING_CODE, Actor.PARTNER, null)).thenThrow(new InvalidTransitionException(
                ParcelState.ON_ITS_WAY, ParcelEvent.CANCEL, Actor.PARTNER, "Too late to cancel"));

        mvc.perform(post("/api/v1/parcels/" + TRACKING_CODE + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INVALID_TRANSITION")))
                .andExpect(jsonPath("$.currentState", is("ON_ITS_WAY")))
                .andExpect(jsonPath("$.allowedEvents", hasItems("DEPOSIT", "DROP_OFF_DEADLINE_PASSED")))
                .andDo(print());

        verify(service).cancel(TRACKING_CODE, Actor.PARTNER, null);
    }

    @Test
    @DisplayName("US5: the courier reports a location")
    void whenPostRequestLocation_thenReturnUpdatedParcel() throws Exception {
        when(service.addLocation(TRACKING_CODE, "Aveiro hub", 40.64, -8.65))
                .thenReturn(tracking(ParcelState.ON_ITS_WAY, ParcelEvent.DEPOSIT));

        mvc.perform(post("/api/v1/parcels/" + TRACKING_CODE + "/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label": "Aveiro hub", "latitude": 40.64, "longitude": -8.65}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("ON_ITS_WAY")))
                .andDo(print());

        verify(service).addLocation(TRACKING_CODE, "Aveiro hub", 40.64, -8.65);
    }

    @Test
    @DisplayName("US5: a latitude out of range is a 400 and the service is never called")
    void whenPostRequestLocationWithLatitudeOutOfRange_thenReturnError400() throws Exception {

        mvc.perform(post("/api/v1/parcels/" + TRACKING_CODE + "/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label": "Aveiro hub", "latitude": 99, "longitude": -8.65}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andDo(print());

        verifyNoInteractions(service);
    }
}
