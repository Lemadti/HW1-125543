package pt.ua.deti.tqs.orbit.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pt.ua.deti.tqs.orbit.adapter.hardware.FakeLockerHardware.Fault;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.port.CompartmentRef;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;
import pt.ua.deti.tqs.orbit.support.AbstractIntegrationTest;

/**
 * Hardware failure rules through the API, with the whole application and the real database.
 *
 * <p>The locker hardware is the in-process {@code FakeLockerHardware} bean; each test injects the fault it needs
 * (door won't open, door won't close, station unreachable) and then acts through the terminal API, as the terminal
 * page does. Incidents and compartment statuses are checked in the database and through the operator API.</p>
 */
@DisplayName("Hardware failures — whole application, fake hardware, real PostgreSQL")
class HardwareFailureIT extends AbstractIntegrationTest {

    private ResultActions startDropOff(String pin) throws Exception {
        return mvc.perform(post("/api/v1/terminals/" + FORUM + "/drop-offs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + pin + "\"}"));
    }

    private static String read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    private String incidentTypes() {
        return String.join(",", jdbc.queryForList("SELECT type FROM incident ORDER BY id", String.class));
    }

    @Test
    @DisplayName("Door doesn't open: replacement compartment, operator incident, and the new code completes the drop-off")
    void givenADoorThatWontOpen_whenDropOff_thenReplacementAndIncident() throws Exception {
        RegistrationResult reg = register(FORUM, "M", "M-07");
        collect(reg);
        hardware.inject(new CompartmentRef(FORUM, "M-07"), Fault.DOOR_WONT_OPEN);

        ResultActions failed = startDropOff(reg.dropOffCode())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", is("DOOR_DID_NOT_OPEN")))
                .andExpect(jsonPath("$.replacementAvailable", is(true)));
        mvc.perform(post("/api/v1/terminals/" + FORUM + "/drop-offs/" + read(failed, "$.sessionToken") + "/replacement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", is("REPLACEMENT_ISSUED")))
                .andExpect(jsonPath("$.compartmentCode", startsWith("M-")));

        String newCompartment = compartmentOf(reg.trackingCode());
        assertThat(newCompartment).startsWith("M-").isNotEqualTo("M-07");
        assertThat(incidentTypes()).isEqualTo("DOOR_DID_NOT_OPEN");
        mvc.perform(get("/api/v1/stations/" + FORUM))
                .andExpect(jsonPath("$.compartments[?(@.code=='M-07')].availability", contains("OUT_OF_SERVICE")));

        // the new code (sent to the sender) opens the new compartment and the drop-off completes
        ResultActions opened = startDropOff(pickupCode(reg.trackingCode()))
                .andExpect(jsonPath("$.outcome", is("DOOR_OPENED")))
                .andExpect(jsonPath("$.compartmentCode", is(newCompartment)));
        mvc.perform(post("/api/v1/terminals/" + FORUM + "/drop-offs/" + read(opened, "$.sessionToken") + "/door-closed"))
                .andExpect(jsonPath("$.outcome", is("DEPOSITED")));
        assertThat(stateOf(reg.trackingCode())).isEqualTo(ParcelState.READY_FOR_PICKUP);
    }

    @Test
    @DisplayName("Door doesn't close: the parcel stays ON_ITS_WAY and the failure is recorded")
    void givenADoorThatWontClose_whenDoorClosed_thenParcelStaysOnItsWay() throws Exception {
        hardware.setAutoClose(false);
        RegistrationResult reg = register(FORUM, "S", "S-01");
        collect(reg);
        hardware.inject(new CompartmentRef(FORUM, "S-01"), Fault.DOOR_WONT_CLOSE);

        ResultActions opened = startDropOff(reg.dropOffCode());
        mvc.perform(post("/api/v1/terminals/" + FORUM + "/drop-offs/" + read(opened, "$.sessionToken") + "/door-closed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", is("DOOR_DID_NOT_CLOSE")));

        assertThat(stateOf(reg.trackingCode())).isEqualTo(ParcelState.ON_ITS_WAY);
        assertThat(incidentTypes()).isEqualTo("DOOR_DID_NOT_CLOSE");
    }

    @Test
    @DisplayName("Interface doesn't respond: 503 HARDWARE_UNAVAILABLE, nothing changes, the operator is told")
    void givenAnUnreachableStation_whenDropOff_thenError503AndNothingChanges() throws Exception {
        RegistrationResult reg = register(FORUM, "S", null);
        collect(reg);
        hardware.setStationUnreachable(true);

        startDropOff(reg.dropOffCode())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code", is("HARDWARE_UNAVAILABLE")));

        assertThat(stateOf(reg.trackingCode())).isEqualTo(ParcelState.ON_ITS_WAY);
        mvc.perform(get("/api/v1/operator/stations/" + FORUM + "/incidents"))
                .andExpect(jsonPath("$[0].type", is("HARDWARE_UNREACHABLE")));
    }

    @Test
    @DisplayName("US6: putting a reserved compartment out of service moves the reservation; back in service frees it")
    void givenAReservedCompartment_whenOutOfService_thenReservationMoves() throws Exception {
        RegistrationResult reg = register(FORUM, "M", "M-11");

        mvc.perform(put("/api/v1/operator/stations/" + FORUM + "/compartments/M-11/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OUT_OF_SERVICE\",\"reason\":\"Broken hinge\"}"))
                .andExpect(status().isNoContent());
        assertThat(compartmentOf(reg.trackingCode())).isNotEqualTo("M-11");

        mvc.perform(put("/api/v1/operator/stations/" + FORUM + "/compartments/M-11/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_SERVICE\"}"))
                .andExpect(status().isNoContent());
        assertThat(register(FORUM, "M", "M-11").compartmentCode()).isEqualTo("M-11");
    }

    @Test
    @DisplayName("US6: out of service is refused (409) when the reservation has nowhere to go")
    void givenEveryLargeCompartmentReserved_whenOutOfService_thenError409() throws Exception {
        register(FORUM, "L", "L-04");
        register(FORUM, "L", "L-08");
        register(FORUM, "L", "L-16");

        mvc.perform(put("/api/v1/operator/stations/" + FORUM + "/compartments/L-04/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OUT_OF_SERVICE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("NO_REPLACEMENT_AVAILABLE")));

        assertThat(jdbc.queryForObject("SELECT c.status FROM compartment c JOIN station s ON s.id = c.station_id "
                + "WHERE s.code = ? AND c.code = 'L-04'", String.class, FORUM)).isEqualTo("IN_SERVICE");
    }
}
