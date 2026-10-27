package pt.ua.deti.tqs.orbit.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.service.DeadlineService;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;
import pt.ua.deti.tqs.orbit.support.AbstractIntegrationTest;

/**
 * Time-based rules with the whole application and the real database.
 *
 * <p>The application clock (a {@code MutableClock} bean) is moved by the test and the deadline job is run explicitly,
 * as the scheduler would: nothing sleeps. Arrange through the back door (services), act through the API (MockMvc),
 * check what was stored (repositories).</p>
 */
@DisplayName("Deadlines — whole application, clock moved, real PostgreSQL")
class DeadlineAndReturnIT extends AbstractIntegrationTest {

    @Autowired
    private DeadlineService deadlines;

    private static String code(String pin) {
        return "{\"code\":\"" + pin + "\"}";
    }

    @Test
    @DisplayName("Drop-off deadline: an uncollected parcel is cancelled by SYSTEM one second after it")
    void givenAnUncollectedParcel_whenTheDropOffDeadlinePasses_thenCancelledAndCompartmentFree() throws Exception {
        RegistrationResult reg = register(FORUM, "M", "M-07");

        clock.setFixed(reg.dropOffDeadline());
        assertThat(deadlines.processDeadlines().cancelled()).isZero();
        clock.setFixed(reg.dropOffDeadline().plusSeconds(1));
        assertThat(deadlines.processDeadlines().cancelled()).isEqualTo(1);

        assertThat(stateOf(reg.trackingCode())).isEqualTo(ParcelState.CANCELLED);
        assertThat(lastActorOf(reg.trackingCode())).isEqualTo("SYSTEM");
        mvc.perform(post("/api/v1/courier/collections").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryCode\":\"" + reg.deliveryCode() + "\"}"))
                .andExpect(status().isConflict());
        assertThat(register(FORUM, "M", "M-07").compartmentCode()).isEqualTo("M-07");
    }

    @Test
    @DisplayName("Drop-off deadline: a collected parcel that was not dropped off is cancelled too")
    void givenACollectedParcel_whenTheDropOffDeadlinePasses_thenCancelled() throws Exception {
        RegistrationResult reg = register(FORUM, "M", "M-07");
        collect(reg);

        clock.setFixed(reg.dropOffDeadline().plusSeconds(1));
        assertThat(deadlines.processDeadlines().cancelled()).isEqualTo(1);

        mvc.perform(get("/api/v1/parcels/" + reg.trackingCode()))
                .andExpect(jsonPath("$.state", is("CANCELLED")))
                .andExpect(jsonPath("$.history[-1:].event", contains("DROP_OFF_DEADLINE_PASSED")));
        assertThat(register(FORUM, "M", "M-07").compartmentCode()).isEqualTo("M-07");
    }

    @Test
    @DisplayName("Pickup deadline: after 15 days the parcel EXPIRES but its code still works")
    void givenAParcelWaiting_whenFifteenDaysPass_thenExpiredButCodeStillWorks() throws Exception {
        RegistrationResult reg = register(FORUM, "S", "S-01");
        String pin = deliverToLocker(FORUM, reg);

        clock.advance(Duration.ofDays(15));
        assertThat(deadlines.processDeadlines().expired()).isEqualTo(1);
        assertThat(stateOf(reg.trackingCode())).isEqualTo(ParcelState.EXPIRED);

        mvc.perform(post("/api/v1/terminals/" + FORUM + "/pickups").contentType(MediaType.APPLICATION_JSON).content(code(pin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("PICKED_UP")));
    }

    @Test
    @DisplayName("Pickup deadline: an expired parcel becomes TO_RETURN when its compartment is reused, RETURNED on the next drop-off")
    void givenAnExpiredParcel_whenItsCompartmentIsReused_thenToReturnAndThenReturned() throws Exception {
        RegistrationResult old = register(FORUM, "L", "L-04");
        deliverToLocker(FORUM, old);
        clock.advance(Duration.ofDays(15));
        deadlines.processDeadlines();

        String body = mvc.perform(post("/api/v1/parcels").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(FORUM, "L", "L-04")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(stateOf(old.trackingCode())).isEqualTo(ParcelState.TO_RETURN);
        assertThat(lastActorOf(old.trackingCode())).isEqualTo("SYSTEM");

        // the courier collects the new parcel and drops it off in L-04, taking the old one out
        String next = JsonPath.read(body, "$.trackingCode");
        parcelService.collect(JsonPath.read(body, "$.deliveryCode"), null);
        String token = JsonPath.read(mvc.perform(post("/api/v1/terminals/" + FORUM + "/drop-offs")
                        .contentType(MediaType.APPLICATION_JSON).content(code(JsonPath.read(body, "$.dropOffCode"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.sessionToken");
        mvc.perform(post("/api/v1/terminals/" + FORUM + "/drop-offs/" + token + "/door-closed"))
                .andExpect(jsonPath("$.outcome", is("DEPOSITED")));

        assertThat(stateOf(old.trackingCode())).isEqualTo(ParcelState.RETURNED);
        assertThat(lastActorOf(old.trackingCode())).isEqualTo("COURIER");
        assertThat(stateOf(next)).isEqualTo(ParcelState.READY_FOR_PICKUP);
        mvc.perform(get("/api/v1/parcels/" + old.trackingCode()))
                .andExpect(jsonPath("$.history[-3:].to", contains("EXPIRED", "TO_RETURN", "RETURNED")));
    }

    @Test
    @DisplayName("US6: the operator lists the expired parcels and registers the removal; the code then stops working")
    void givenAnExpiredParcel_whenTheOperatorRegistersTheRemoval_thenReturned() throws Exception {
        RegistrationResult reg = register(FORUM, "M", "M-03");
        String pin = deliverToLocker(FORUM, reg);
        clock.advance(Duration.ofDays(16));
        deadlines.processDeadlines();

        mvc.perform(get("/api/v1/operator/stations/" + FORUM + "/parcels").param("state", "EXPIRED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].trackingCode", contains(reg.trackingCode())));
        mvc.perform(post("/api/v1/operator/parcels/" + reg.trackingCode() + "/removal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("RETURNED")));

        assertThat(stateOf(reg.trackingCode())).isEqualTo(ParcelState.RETURNED);
        assertThat(lastActorOf(reg.trackingCode())).isEqualTo("OPERATOR");
        mvc.perform(post("/api/v1/terminals/" + FORUM + "/pickups").contentType(MediaType.APPLICATION_JSON).content(code(pin)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("Codes: the terminal block ends after one hour of clock time")
    void givenABlockedTerminal_whenOneHourPasses_thenCodesAreCheckedAgain() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/terminals/" + FORUM + "/pickups").contentType(MediaType.APPLICATION_JSON)
                    .content(code("00000" + i)));
        }
        mvc.perform(post("/api/v1/terminals/" + FORUM + "/pickups").contentType(MediaType.APPLICATION_JSON).content(code("999999")))
                .andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofHours(1));

        mvc.perform(post("/api/v1/terminals/" + FORUM + "/pickups").contentType(MediaType.APPLICATION_JSON).content(code("999999")))
                .andExpect(status().isUnprocessableContent());
    }
}
