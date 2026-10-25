package pt.ua.deti.tqs.orbit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import pt.ua.deti.tqs.orbit.support.AbstractIntegrationTest;

/** The REST API end to end with the real database: every use case and the explicit rejections. */
class ParcelLifecycleApiIT extends AbstractIntegrationTest {

    @Test
    void fullHappyPathRecordsEveryTransitionWithActor() {
        JsonNode reg = register(FORUM, "M", "M-07", 48);
        assertThat(reg.get("compartmentCode").asString()).isEqualTo("M-07");
        String tc = reg.get("trackingCode").asString();

        String pin = deliverToLocker(FORUM, reg);
        assertThat(state(tc)).isEqualTo("READY_FOR_PICKUP");

        JsonNode opened = json(post("/api/v1/terminals/" + FORUM + "/pickups", "{\"code\":\"" + pin + "\"}"));
        assertThat(opened.get("outcome").asString()).isEqualTo("DOOR_OPENED");
        post("/api/v1/terminals/" + FORUM + "/pickups/" + opened.get("sessionToken").asString() + "/door-closed", null);

        JsonNode track = json(get("/api/v1/parcels/" + tc));
        assertThat(track.get("state").asString()).isEqualTo("PICKED_UP");
        assertThat(track.get("recipientName").asString()).isEqualTo("João S.");
        assertThat(track.get("history")).extracting(h -> h.get("to").asString() + "/" + h.get("actor").asString())
                .containsExactly("CREATED/PARTNER", "ON_ITS_WAY/COURIER", "READY_FOR_PICKUP/COURIER", "PICKED_UP/RECIPIENT");
        assertThat(track.get("history")).allSatisfy(h -> assertThat(h.get("at").asString()).isNotBlank());
    }

    @Test
    void compartmentIsFreedAfterPickupAndAvailabilityIsReported() {
        long before = json(get("/api/v1/stations/" + FORUM)).get("station").get("free").get("m").asLong();
        JsonNode reg = register(FORUM, "M", null, 24);
        assertThat(json(get("/api/v1/stations/" + FORUM)).get("station").get("free").get("m").asLong()).isEqualTo(before - 1);

        String pin = deliverToLocker(FORUM, reg);
        post("/api/v1/terminals/" + FORUM + "/pickups", "{\"code\":\"" + pin + "\"}");

        assertThat(json(get("/api/v1/stations/" + FORUM)).get("station").get("free").get("m").asLong()).isEqualTo(before);
    }

    @Test
    void pickupCodeIsSingleUse() {
        JsonNode reg = register(FORUM, "S", null, 24);
        String pin = deliverToLocker(FORUM, reg);
        assertThat(post("/api/v1/terminals/" + FORUM + "/pickups", "{\"code\":\"" + pin + "\"}"))
                .hasStatus(HttpStatus.OK);
        assertThat(post("/api/v1/terminals/" + FORUM + "/pickups", "{\"code\":\"" + pin + "\"}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_CODE");
    }

    @Test
    void pickupCodeOnlyWorksAtItsStation() {
        JsonNode reg = register(FORUM, "S", null, 24);
        String pin = deliverToLocker(FORUM, reg);
        assertThat(post("/api/v1/terminals/ORB-AVR-002/pickups", "{\"code\":\"" + pin + "\"}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void invalidEventsAreRejectedExplicitly() {
        JsonNode reg = register(FORUM, "L", null, 24);
        String tc = reg.get("trackingCode").asString();

        // drop-off before the courier collected it
        assertThat(post("/api/v1/terminals/" + FORUM + "/drop-offs", "{\"code\":\"" + reg.get("dropOffCode").asString() + "\"}"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.currentState").isEqualTo("CREATED");
        // location update while CREATED
        assertThat(post("/api/v1/parcels/" + tc + "/locations", "{\"label\":\"Depot\"}")).hasStatus(HttpStatus.CONFLICT);
        // operator removal of a parcel that is not expired
        assertThat(post("/api/v1/operator/parcels/" + tc + "/removal", null)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_TRANSITION");

        collect(reg);
        // cancel after collection
        assertThat(post("/api/v1/parcels/" + tc + "/cancel", null)).hasStatus(HttpStatus.CONFLICT);
        // collecting twice
        assertThat(post("/api/v1/courier/collections", "{\"deliveryCode\":\"" + reg.get("deliveryCode").asString() + "\"}"))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(state(tc)).isEqualTo("ON_ITS_WAY");
    }

    @Test
    void cancellationFreesTheCompartment() {
        JsonNode reg = register(FORUM, "M", "M-11", 24);
        assertThat(post("/api/v1/parcels/" + reg.get("trackingCode").asString() + "/cancel",
                "{\"actor\":\"RECIPIENT\",\"reason\":\"changed my mind\"}")).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.state").isEqualTo("CANCELLED");
        assertThat(register(FORUM, "M", "M-11", 24).get("compartmentCode").asString()).isEqualTo("M-11");
    }

    @Test
    void chosenCompartmentAlreadyTakenIs409() {
        register(FORUM, "M", "M-15", 24);
        assertThat(post("/api/v1/parcels", registerBody(FORUM, "M", "M-15", 24))).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("COMPARTMENT_NOT_AVAILABLE");
    }

    @Test
    void stationFullWhenNoCompatibleCompartmentLeft() {
        register(FORUM, "L", null, 24);
        register(FORUM, "L", null, 24);
        register(FORUM, "L", null, 24);
        assertThat(post("/api/v1/parcels", registerBody(FORUM, "L", null, 24))).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("STATION_FULL");
        assertThat(get("/api/v1/stations?size=L")).bodyJson().extractingPath("$[?(@.code=='ORB-AVR-001')]").asArray().isEmpty();
    }

    @Test
    void nearbySearchUsesTheGeocoderAndRanksByDistance() {
        assertThat(mvc.get().uri("/api/v1/stations/nearby").param("address", "Rua de Coimbra 10, Aveiro")).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.stations[0].code").isEqualTo("ORB-AVR-001");
        assertThat(mvc.get().uri("/api/v1/stations/nearby").param("address", "Unknown street 999")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("ADDRESS_NOT_FOUND");
    }

    @Test
    void withoutARadiusEveryStationIsRankedAndWithOneTheFarOnesAreLeftOut() {
        assertThat(mvc.get().uri("/api/v1/stations/nearby").param("address", "Rua de Coimbra 10, Aveiro"))
                .hasStatus(HttpStatus.OK).bodyJson()
                .hasPathSatisfying("$.stations.length()", v -> v.assertThat().isEqualTo(5))
                .hasPathSatisfying("$.radiusKm", v -> v.assertThat().isNull());

        // Ílhavo Centro is ~4.6 km away: inside 5 km, outside 2 km
        assertThat(mvc.get().uri("/api/v1/stations/nearby").param("address", "Rua de Coimbra 10, Aveiro")
                .param("radiusKm", "2"))
                .hasStatus(HttpStatus.OK).bodyJson()
                .hasPathSatisfying("$.stations.length()", v -> v.assertThat().isEqualTo(4))
                .hasPathSatisfying("$.stations[?(@.code=='ORB-AVR-005')]", v -> v.assertThat().asArray().isEmpty())
                .hasPathSatisfying("$.radiusKm", v -> v.assertThat().isEqualTo(2.0));
        assertThat(mvc.get().uri("/api/v1/stations/nearby").param("address", "Rua de Coimbra 10, Aveiro")
                .param("radiusKm", "5"))
                .bodyJson().extractingPath("$.stations.length()").isEqualTo(5);
    }

    @Test
    void radiusAboveTheConfiguredMaximumIs422() {
        assertThat(mvc.get().uri("/api/v1/stations/nearby").param("address", "Rua de Coimbra 10, Aveiro")
                .param("radiusKm", "500"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson()
                .hasPathSatisfying("$.code", v -> v.assertThat().isEqualTo("INVALID_RADIUS"))
                .hasPathSatisfying("$.maxRadiusKm", v -> v.assertThat().isEqualTo(20.0));
        assertThat(get("/api/v1/config")).bodyJson().extractingPath("$.radiusOptionsKm").asArray()
                .containsExactly(1.0, 2.0, 5.0, 10.0, 20.0);
    }

    @Test
    void geocoderUnavailableReturns503AndTheFallbackStillLists() {
        ((pt.ua.deti.tqs.orbit.adapter.geocoding.StubGeocoder) geocoder).setUnavailable(true);
        assertThat(mvc.get().uri("/api/v1/stations/nearby").param("address", "Rua de Coimbra 10, Aveiro")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(get("/api/v1/stations")).hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.length()").isEqualTo(5);
    }

    @Test
    void openApiDocumentIsPublished() {
        assertThat(get("/v3/api-docs")).hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.info.title")
                .isEqualTo("Orbit Lockers API");
    }
}
