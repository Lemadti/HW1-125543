package pt.ua.deti.tqs.orbit.web.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.service.TerminalService;
import pt.ua.deti.tqs.orbit.service.view.TerminalResult;

/**
 * <b>C</b> — the locker terminal controller, in a web "slice".
 *
 * <p>{@code @WebMvcTest} starts only the web layer and the {@link ApiExceptionHandler}; the controller's
 * {@link TerminalService} is a Mockito mock ({@code @MockitoBean}) and requests go through {@link MockMvc}. No
 * hardware, no database. The terminal rules are tested in {@code TerminalServiceTest}; here the oracle is the HTTP
 * contract the terminal page relies on.</p>
 */
@WebMvcTest(TerminalApiController.class)
@EnableConfigurationProperties(OrbitProperties.class)
@DisplayName("TerminalApiController (web slice, service mocked)")
class TerminalApiControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TerminalService service;

    private static final String STATION = "ORB-AVR-001";
    private static final String BASE = "/api/v1/terminals/" + STATION;
    private static final String PHONE = "+351 234 100 200";

    private static TerminalResult result(String outcome, ParcelState state, String token) {
        return new TerminalResult(outcome, "message", "ORB-261024-0714", state, "M-07", token, null, PHONE);
    }

    @Test
    @DisplayName("US3: a valid drop-off PIN opens the door and returns a session token")
    void whenPostDropOff_thenReturnDoorOpenedWithSessionToken() throws Exception {
        when(service.startDropOff(STATION, "123456")).thenReturn(result("DOOR_OPENED", ParcelState.ON_ITS_WAY, "T1"));

        mvc.perform(post(BASE + "/drop-offs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "123456"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", is("DOOR_OPENED")))
                .andExpect(jsonPath("$.compartmentCode", is("M-07")))
                .andExpect(jsonPath("$.sessionToken", is("T1")))
                .andExpect(jsonPath("$.assistancePhone", is(PHONE)))
                .andDo(print());

        verify(service).startDropOff(STATION, "123456");
    }

    @Test
    @DisplayName("US3: closing the door confirms the drop-off with the session token")
    void whenPostDoorClosed_thenReturnDeposited() throws Exception {
        when(service.confirmDropOff(STATION, "T1")).thenReturn(result("DEPOSITED", ParcelState.READY_FOR_PICKUP, null));

        mvc.perform(post(BASE + "/drop-offs/T1/door-closed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", is("DEPOSITED")))
                .andExpect(jsonPath("$.state", is("READY_FOR_PICKUP")))
                .andDo(print());

        verify(service).confirmDropOff(STATION, "T1");
    }

    @Test
    @DisplayName("Door doesn't open: asking for a replacement returns the new compartment")
    void whenPostReplacement_thenReturnReplacementIssued() throws Exception {
        when(service.requestReplacement(STATION, "T1"))
                .thenReturn(result("REPLACEMENT_ISSUED", ParcelState.ON_ITS_WAY, null));

        mvc.perform(post(BASE + "/drop-offs/T1/replacement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", is("REPLACEMENT_ISSUED")))
                .andDo(print());

        verify(service).requestReplacement(STATION, "T1");
    }

    @Test
    @DisplayName("US4: a valid pickup code opens the door; closing it completes the pickup")
    void whenPostPickupAndDoorClosed_thenReturnCompleted() throws Exception {
        when(service.startPickup(STATION, "654321")).thenReturn(result("DOOR_OPENED", ParcelState.PICKED_UP, "T2"));
        when(service.confirmPickup(STATION, "T2")).thenReturn(result("COMPLETED", ParcelState.PICKED_UP, null));

        mvc.perform(post(BASE + "/pickups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "654321"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("PICKED_UP")));
        mvc.perform(post(BASE + "/pickups/T2/door-closed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", is("COMPLETED")))
                .andDo(print());

        verify(service).startPickup(STATION, "654321");
        verify(service).confirmPickup(STATION, "T2");
    }

    @Test
    @DisplayName("Codes: a blank code is a 400 and the service is never called")
    void whenPostBlankCode_thenReturnError400() throws Exception {

        mvc.perform(post(BASE + "/drop-offs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": ""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andDo(print());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Codes: a wrong code is a 422 INVALID_CODE with the attempts left")
    void whenPostWrongCode_thenReturnError422WithRemainingAttempts() throws Exception {
        when(service.startPickup(STATION, "000000")).thenThrow(new OrbitException(OrbitException.Kind.UNPROCESSABLE,
                "INVALID_CODE", "This code is not valid", Map.of("remainingAttempts", 2)));

        mvc.perform(post(BASE + "/pickups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "000000"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", is("INVALID_CODE")))
                .andExpect(jsonPath("$.remainingAttempts", is(2)))
                .andDo(print());
    }

    @Test
    @DisplayName("Codes: a blocked terminal is a 429 with Retry-After")
    void whenTerminalIsBlocked_thenReturnError429WithRetryAfter() throws Exception {
        when(service.startPickup(STATION, "000000")).thenThrow(new OrbitException(OrbitException.Kind.LOCKED,
                "TOO_MANY_ATTEMPTS", "blocked", Map.of("lockedUntil", Instant.now().plusSeconds(3600).toString())));

        mvc.perform(post(BASE + "/pickups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "000000"}"""))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code", is("TOO_MANY_ATTEMPTS")))
                .andDo(print());
    }

    @Test
    @DisplayName("Interface doesn't respond: the hardware down is a 503 with the assistance number")
    void whenHardwareIsUnavailable_thenReturnError503() throws Exception {
        when(service.startDropOff(STATION, "123456")).thenThrow(new OrbitException(OrbitException.Kind.UNAVAILABLE,
                "HARDWARE_UNAVAILABLE", "The locker is not responding", Map.of("assistancePhone", PHONE)));

        mvc.perform(post(BASE + "/drop-offs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "123456"}"""))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code", is("HARDWARE_UNAVAILABLE")))
                .andExpect(jsonPath("$.assistancePhone", is(PHONE)))
                .andDo(print());
    }
}
