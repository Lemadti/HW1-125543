package pt.ua.deti.tqs.orbit.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pt.ua.deti.tqs.orbit.adapter.geocoding.StubGeocoder;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;
import pt.ua.deti.tqs.orbit.support.AbstractIntegrationTest;

/**
 * <b>D</b> — the REST API of the whole application, through MockMvc, with the real database.
 *
 * <p>Nothing is mocked: controller → service → repository → PostgreSQL (see {@link AbstractIntegrationTest}). Each
 * test acts through the API, as a partner, courier, terminal or recipient client would, and checks both the HTTP
 * answer and what was stored (the back door: the repositories).</p>
 */
@DisplayName("Parcel REST API — whole application through MockMvc, real PostgreSQL")
class ParcelRestControllerIT extends AbstractIntegrationTest {

    private static String code(String pin) {
        return "{\"code\":\"" + pin + "\"}";
    }

    @Test
    @DisplayName("US2: a valid registration is stored as CREATED in the chosen compartment")
    void whenValidInput_thenRegisterParcel() throws Exception {
        mvc.perform(post("/api/v1/parcels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(FORUM, "M", "M-07")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/v1/parcels/ORB-\\d{6}-\\d{4}")))
                .andExpect(jsonPath("$.compartmentCode", is("M-07")))
                .andExpect(jsonPath("$.dropOffCode", matchesPattern("\\d{6}")));

        assertThat(parcels.findAll()).singleElement().satisfies(p -> {
            assertThat(p.getState()).isEqualTo(ParcelState.CREATED);
            assertThat(p.getDropOffCodeHash()).hasSize(64);
        });
    }

    @Test
    @DisplayName("US5: the whole journey is tracked with every transition and its actor")
    void givenADeliveredAndPickedUpParcel_whenTrack_thenHistoryHasEveryTransitionWithActor() throws Exception {
        RegistrationResult reg = register(FORUM, "M", "M-07");
        String pin = deliverToLocker(FORUM, reg);
        String token = terminalService.startPickup(FORUM, pin).sessionToken();
        terminalService.confirmPickup(FORUM, token);

        mvc.perform(get("/api/v1/parcels/" + reg.trackingCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("PICKED_UP")))
                .andExpect(jsonPath("$.recipientName", is("João S.")))
                .andExpect(jsonPath("$.history[*].to", contains("CREATED", "ON_ITS_WAY", "READY_FOR_PICKUP", "PICKED_UP")))
                .andExpect(jsonPath("$.history[*].actor", contains("PARTNER", "COURIER", "COURIER", "RECIPIENT")))
                .andExpect(jsonPath("$.history[*].at", everyItem(notNullValue())));
    }

    @Test
    @DisplayName("US4: picking up frees the compartment again")
    void givenAParcelWaiting_whenPickedUp_thenCompartmentIsFreeAgain() throws Exception {
        long before = free("M");
        RegistrationResult reg = register(FORUM, "M", null);
        assertThat(free("M")).isEqualTo(before - 1);
        String pin = deliverToLocker(FORUM, reg);

        mvc.perform(post("/api/v1/terminals/" + FORUM + "/pickups").contentType(MediaType.APPLICATION_JSON).content(code(pin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome", is("DOOR_OPENED")));

        assertThat(free("M")).isEqualTo(before);
        assertThat(stateOf(reg.trackingCode())).isEqualTo(ParcelState.PICKED_UP);
    }

    /** Back door: free compartments of that size at Fórum Aveiro, counted in the database. */
    private long free(String size) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM compartment c JOIN station s ON s.id = c.station_id
                WHERE s.code = ? AND c.size = ? AND c.status = 'IN_SERVICE' AND NOT EXISTS (
                  SELECT 1 FROM parcel p WHERE p.compartment_id = c.id AND p.state IN ('CREATED','ON_ITS_WAY','READY_FOR_PICKUP'))""",
                Long.class, FORUM, size);
    }

    @Test
    @DisplayName("NFR6: the pickup code works once; the second time it is a 422 INVALID_CODE")
    void givenAPickedUpParcel_whenTheSameCodeIsUsedAgain_thenError422() throws Exception {
        String pin = deliverToLocker(FORUM, register(FORUM, "S", null));
        mvc.perform(post("/api/v1/terminals/" + FORUM + "/pickups").contentType(MediaType.APPLICATION_JSON).content(code(pin)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/terminals/" + FORUM + "/pickups").contentType(MediaType.APPLICATION_JSON).content(code(pin)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", is("INVALID_CODE")));
    }

    @Test
    @DisplayName("Pickup code: a code only works at its own station")
    void givenAPickupCode_whenUsedAtAnotherStation_thenError422() throws Exception {
        RegistrationResult reg = register(FORUM, "S", null);
        String pin = deliverToLocker(FORUM, reg);

        mvc.perform(post("/api/v1/terminals/ORB-AVR-002/pickups").contentType(MediaType.APPLICATION_JSON).content(code(pin)))
                .andExpect(status().isUnprocessableContent());

        assertThat(stateOf(reg.trackingCode())).isEqualTo(ParcelState.READY_FOR_PICKUP);
    }

    @Test
    @DisplayName("State machine: events not allowed in the current state are 409 and change nothing")
    void givenACreatedParcel_whenInvalidEvents_thenError409AndStateUnchanged() throws Exception {
        RegistrationResult reg = register(FORUM, "L", null);
        String tc = reg.trackingCode();

        // drop-off before the courier collected it
        mvc.perform(post("/api/v1/terminals/" + FORUM + "/drop-offs").contentType(MediaType.APPLICATION_JSON)
                        .content(code(reg.dropOffCode())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentState", is("CREATED")));
        // location update while CREATED
        mvc.perform(post("/api/v1/parcels/" + tc + "/locations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Depot\"}"))
                .andExpect(status().isConflict());
        // operator removal of a parcel that is not expired
        mvc.perform(post("/api/v1/operator/parcels/" + tc + "/removal"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INVALID_TRANSITION")));
        assertThat(stateOf(tc)).isEqualTo(ParcelState.CREATED);

        collect(reg);
        // cancel after collection, and collecting twice
        mvc.perform(post("/api/v1/parcels/" + tc + "/cancel"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/courier/collections").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryCode\":\"" + reg.deliveryCode() + "\"}"))
                .andExpect(status().isConflict());
        assertThat(stateOf(tc)).isEqualTo(ParcelState.ON_ITS_WAY);
    }

    @Test
    @DisplayName("Compartment allocation: cancelling frees the compartment for the next parcel")
    void givenACreatedParcel_whenCancelledByTheRecipient_thenCompartmentIsFree() throws Exception {
        RegistrationResult reg = register(FORUM, "M", "M-11");

        mvc.perform(post("/api/v1/parcels/" + reg.trackingCode() + "/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"RECIPIENT\",\"reason\":\"changed my mind\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("CANCELLED")));

        assertThat(stateOf(reg.trackingCode())).isEqualTo(ParcelState.CANCELLED);
        assertThat(lastActorOf(reg.trackingCode())).isEqualTo("RECIPIENT");
        assertThat(register(FORUM, "M", "M-11").compartmentCode()).isEqualTo("M-11");
    }

    @Test
    @DisplayName("Compartment allocation: a chosen compartment that is already reserved is a 409")
    void givenAReservedCompartment_whenChosenAgain_thenError409() throws Exception {
        register(FORUM, "M", "M-15");

        mvc.perform(post("/api/v1/parcels").contentType(MediaType.APPLICATION_JSON).content(registerBody(FORUM, "M", "M-15")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("COMPARTMENT_NOT_AVAILABLE")));

        assertThat(parcels.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Compartment allocation: no free compartment of that size is a 409 STATION_FULL")
    void givenAllLargeCompartmentsReserved_whenRegisterLarge_thenError409() throws Exception {
        register(FORUM, "L", null);
        register(FORUM, "L", null);
        register(FORUM, "L", null);

        mvc.perform(post("/api/v1/parcels").contentType(MediaType.APPLICATION_JSON).content(registerBody(FORUM, "L", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("STATION_FULL")));
        mvc.perform(get("/api/v1/stations").param("size", "L"))
                .andExpect(jsonPath("$[*].code", not(hasItem(FORUM))));
    }

    @Test
    @DisplayName("US1: an address is geocoded and every station is ranked by distance")
    void whenSearchNearAnAddress_thenStationsRankedByDistance() throws Exception {
        mvc.perform(get("/api/v1/stations/nearby").param("address", "Rua de Coimbra 10, Aveiro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stations", hasSize(5)))
                .andExpect(jsonPath("$.stations[0].code", is("ORB-AVR-001")))
                .andExpect(jsonPath("$.radiusKm", nullValue()));
        mvc.perform(get("/api/v1/stations/nearby").param("address", "Unknown street 999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ADDRESS_NOT_FOUND")));
    }

    @Test
    @DisplayName("US1: with a 2 km radius Ílhavo Centro (~4.6 km) is left out; with 5 km it is in")
    void whenSearchWithARadius_thenFarStationsAreLeftOut() throws Exception {
        mvc.perform(get("/api/v1/stations/nearby").param("address", "Rua de Coimbra 10, Aveiro").param("radiusKm", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stations", hasSize(4)))
                .andExpect(jsonPath("$.stations[*].code", not(hasItem("ORB-AVR-005"))))
                .andExpect(jsonPath("$.radiusKm", is(2.0)));
        mvc.perform(get("/api/v1/stations/nearby").param("address", "Rua de Coimbra 10, Aveiro").param("radiusKm", "5"))
                .andExpect(jsonPath("$.stations", hasSize(5)));
    }

    @Test
    @DisplayName("US1: a radius above the configured maximum is a 422 and the options come from the configuration")
    void whenRadiusAboveTheMaximum_thenError422() throws Exception {
        mvc.perform(get("/api/v1/stations/nearby").param("address", "Rua de Coimbra 10, Aveiro").param("radiusKm", "500"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", is("INVALID_RADIUS")))
                .andExpect(jsonPath("$.maxRadiusKm", is(20.0)));
        mvc.perform(get("/api/v1/config"))
                .andExpect(jsonPath("$.radiusOptionsKm", contains(1.0, 2.0, 5.0, 10.0, 20.0)));
    }

    @Test
    @DisplayName("NFR4: the geocoder down is a 503 and the list of all stations still works")
    void givenTheGeocoderDown_whenSearch_thenError503AndFallbackWorks() throws Exception {
        ((StubGeocoder) geocoder).setUnavailable(true);

        mvc.perform(get("/api/v1/stations/nearby").param("address", "Rua de Coimbra 10, Aveiro"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder(
                        "ORB-AVR-001", "ORB-AVR-002", "ORB-AVR-003", "ORB-AVR-004", "ORB-AVR-005")));
    }

    @Test
    @DisplayName("the OpenAPI document is published")
    void whenGetApiDocs_thenOpenApiDocumentIsPublished() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title", is("Orbit Lockers API")));
    }
}
