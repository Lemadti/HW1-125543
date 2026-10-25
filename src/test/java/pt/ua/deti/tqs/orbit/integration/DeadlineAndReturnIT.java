package pt.ua.deti.tqs.orbit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import pt.ua.deti.tqs.orbit.service.DeadlineService;
import pt.ua.deti.tqs.orbit.support.AbstractIntegrationTest;

/** Time-based rules with the real database. The clock is moved; nothing sleeps. */
class DeadlineAndReturnIT extends AbstractIntegrationTest {

    @Autowired
    DeadlineService deadlines;

    @Test
    void leaseEndCancelsAnUncollectedParcelAndFreesTheCompartment() {
        JsonNode reg = register(FORUM, "M", "M-07", 24);
        String tc = reg.get("trackingCode").asString();

        clock.advance(Duration.ofHours(23).plusMinutes(59));
        assertThat(deadlines.processDeadlines().cancelled()).isZero();

        clock.advance(Duration.ofMinutes(1));
        assertThat(deadlines.processDeadlines().cancelled()).isEqualTo(1);
        JsonNode track = json(get("/api/v1/parcels/" + tc));
        assertThat(track.get("state").asString()).isEqualTo("CANCELLED");
        JsonNode last = track.get("history").get(track.get("history").size() - 1);
        assertThat(last.get("actor").asString()).isEqualTo("SYSTEM");
        assertThat(last.get("event").asString()).isEqualTo("DROP_OFF_DEADLINE_PASSED");

        assertThat(register(FORUM, "M", "M-07", 24).get("compartmentCode").asString()).isEqualTo("M-07");
        // the courier can no longer collect the cancelled parcel
        assertThat(post("/api/v1/courier/collections", "{\"deliveryCode\":\"" + reg.get("deliveryCode").asString() + "\"}"))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void pickupWindowEndExpiresTheParcelButTheCodeStillWorks() {
        JsonNode reg = register(FORUM, "S", "S-01", 24);
        String pin = deliverToLocker(FORUM, reg);
        String tc = reg.get("trackingCode").asString();

        clock.advance(Duration.ofDays(15));
        assertThat(deadlines.processDeadlines().expired()).isEqualTo(1);
        assertThat(state(tc)).isEqualTo("EXPIRED");

        assertThat(post("/api/v1/terminals/" + FORUM + "/pickups", "{\"code\":\"" + pin + "\"}"))
                .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.state").isEqualTo("PICKED_UP");
    }

    @Test
    void expiredCompartmentIsReallocatedAndTheOldParcelIsReturnedOnTheNextDropOff() {
        JsonNode old = register(FORUM, "L", "L-04", 24);
        deliverToLocker(FORUM, old);
        clock.advance(Duration.ofDays(15));
        deadlines.processDeadlines();
        String oldTc = old.get("trackingCode").asString();

        // the expired compartment is allocatable again; allocation flags the old parcel TO_RETURN (system)
        JsonNode next = register(FORUM, "L", "L-04", 24);
        assertThat(state(oldTc)).isEqualTo("TO_RETURN");

        deliverToLocker(FORUM, next);
        assertThat(state(oldTc)).isEqualTo("RETURNED");
        JsonNode history = json(get("/api/v1/parcels/" + oldTc)).get("history");
        assertThat(history).extracting(h -> h.get("to").asString() + "/" + h.get("actor").asString())
                .endsWith("EXPIRED/SYSTEM", "TO_RETURN/SYSTEM", "RETURNED/COURIER");
        assertThat(state(next.get("trackingCode").asString())).isEqualTo("READY_FOR_PICKUP");
    }

    @Test
    void operatorRegistersRemovalOfAnExpiredParcel() {
        JsonNode reg = register(FORUM, "M", "M-03", 24);
        String pin = deliverToLocker(FORUM, reg);
        clock.advance(Duration.ofDays(16));
        deadlines.processDeadlines();
        String tc = reg.get("trackingCode").asString();

        assertThat(get("/api/v1/operator/stations/" + FORUM + "/parcels?state=EXPIRED"))
                .bodyJson().extractingPath("$[0].trackingCode").isEqualTo(tc);
        assertThat(post("/api/v1/operator/parcels/" + tc + "/removal", null)).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.state").isEqualTo("RETURNED");
        // after removal the recipient can no longer collect it
        assertThat(post("/api/v1/terminals/" + FORUM + "/pickups", "{\"code\":\"" + pin + "\"}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void lockoutEndsAfterOneHourOfClockTime() {
        for (int i = 0; i < 3; i++) {
            post("/api/v1/terminals/" + FORUM + "/pickups", "{\"code\":\"00000" + i + "\"}");
        }
        assertThat(post("/api/v1/terminals/" + FORUM + "/pickups", "{\"code\":\"999999\"}"))
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        clock.advance(Duration.ofHours(1));
        assertThat(post("/api/v1/terminals/" + FORUM + "/pickups", "{\"code\":\"999999\"}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
