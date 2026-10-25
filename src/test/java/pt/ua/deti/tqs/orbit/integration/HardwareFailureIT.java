package pt.ua.deti.tqs.orbit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import pt.ua.deti.tqs.orbit.adapter.hardware.FakeLockerHardware.Fault;
import pt.ua.deti.tqs.orbit.port.CompartmentRef;
import pt.ua.deti.tqs.orbit.support.AbstractIntegrationTest;

/** Hardware failure rules through the API, with the fake hardware injected into the running application. */
class HardwareFailureIT extends AbstractIntegrationTest {

    private JsonNode startDropOff(String pin) {
        return json(post("/api/v1/terminals/" + FORUM + "/drop-offs", "{\"code\":\"" + pin + "\"}"));
    }

    @Test
    void doorThatDoesNotOpenLeadsToReplacementCompartmentAndOperatorIncident() {
        JsonNode reg = register(FORUM, "M", "M-07", 24);
        collect(reg);
        hardware.inject(new CompartmentRef(FORUM, "M-07"), Fault.DOOR_WONT_OPEN);

        JsonNode failed = startDropOff(reg.get("dropOffCode").asString());
        assertThat(failed.get("outcome").asString()).isEqualTo("DOOR_DID_NOT_OPEN");
        assertThat(failed.get("replacementAvailable").asBoolean()).isTrue();

        JsonNode replaced = json(post("/api/v1/terminals/" + FORUM + "/drop-offs/"
                + failed.get("sessionToken").asString() + "/replacement", null));
        assertThat(replaced.get("outcome").asString()).isEqualTo("REPLACEMENT_ISSUED");
        String newCompartment = replaced.get("compartmentCode").asString();
        assertThat(newCompartment).startsWith("M-").isNotEqualTo("M-07");

        // the faulty compartment is out of service and the incident is visible to the operator
        assertThat(get("/api/v1/stations/" + FORUM)).bodyJson()
                .extractingPath("$.compartments[?(@.code=='M-07')].availability").asArray().containsExactly("OUT_OF_SERVICE");
        assertThat(get("/api/v1/operator/stations/" + FORUM + "/incidents")).bodyJson()
                .extractingPath("$[0].type").isEqualTo("DOOR_DID_NOT_OPEN");

        // the new code (sent to the sender) opens the new compartment and the drop-off completes
        String newPin = pickupCode(reg.get("trackingCode").asString());
        JsonNode opened = startDropOff(newPin);
        assertThat(opened.get("outcome").asString()).isEqualTo("DOOR_OPENED");
        assertThat(opened.get("compartmentCode").asString()).isEqualTo(newCompartment);
        post("/api/v1/terminals/" + FORUM + "/drop-offs/" + opened.get("sessionToken").asString() + "/door-closed", null);
        assertThat(state(reg.get("trackingCode").asString())).isEqualTo("READY_FOR_PICKUP");
    }

    @Test
    void doorThatDoesNotCloseKeepsParcelInTransit() {
        hardware.setAutoClose(false);
        JsonNode reg = register(FORUM, "S", "S-01", 24);
        collect(reg);
        hardware.inject(new CompartmentRef(FORUM, "S-01"), Fault.DOOR_WONT_CLOSE);

        JsonNode opened = startDropOff(reg.get("dropOffCode").asString());
        JsonNode closed = json(post("/api/v1/terminals/" + FORUM + "/drop-offs/"
                + opened.get("sessionToken").asString() + "/door-closed", null));

        assertThat(closed.get("outcome").asString()).isEqualTo("DOOR_DID_NOT_CLOSE");
        assertThat(state(reg.get("trackingCode").asString())).isEqualTo("ON_ITS_WAY");
    }

    @Test
    void unreachableControllerReturns503AndNothingChanges() {
        JsonNode reg = register(FORUM, "S", null, 24);
        collect(reg);
        hardware.setStationUnreachable(true);

        assertThat(post("/api/v1/terminals/" + FORUM + "/drop-offs", "{\"code\":\"" + reg.get("dropOffCode").asString() + "\"}"))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyJson().extractingPath("$.code").isEqualTo("HARDWARE_UNAVAILABLE");
        assertThat(state(reg.get("trackingCode").asString())).isEqualTo("ON_ITS_WAY");
        assertThat(get("/api/v1/operator/stations/" + FORUM + "/incidents")).bodyJson()
                .extractingPath("$[0].type").isEqualTo("HARDWARE_UNREACHABLE");
    }

    @Test
    void operatorPutsReservedCompartmentOutOfServiceAndTheReservationMoves() {
        JsonNode reg = register(FORUM, "M", "M-11", 24);
        assertThat(mvc.put().uri("/api/v1/operator/stations/" + FORUM + "/compartments/M-11/status")
                .contentType("application/json").content("{\"status\":\"OUT_OF_SERVICE\",\"reason\":\"Broken hinge\"}"))
                .hasStatus(HttpStatus.NO_CONTENT);

        JsonNode track = json(get("/api/v1/parcels/" + reg.get("trackingCode").asString()));
        assertThat(track.get("compartmentCode").asString()).isNotEqualTo("M-11");
        // and back in service
        assertThat(mvc.put().uri("/api/v1/operator/stations/" + FORUM + "/compartments/M-11/status")
                .contentType("application/json").content("{\"status\":\"IN_SERVICE\"}")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(register(FORUM, "M", "M-11", 24).get("compartmentCode").asString()).isEqualTo("M-11");
    }

    @Test
    void outOfServiceIsRejectedWhenTheReservationCannotMove() {
        register(FORUM, "L", "L-04", 24);
        register(FORUM, "L", "L-08", 24);
        register(FORUM, "L", "L-16", 24);
        assertThat(mvc.put().uri("/api/v1/operator/stations/" + FORUM + "/compartments/L-04/status")
                .contentType("application/json").content("{\"status\":\"OUT_OF_SERVICE\"}"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("NO_REPLACEMENT_AVAILABLE");
    }
}
