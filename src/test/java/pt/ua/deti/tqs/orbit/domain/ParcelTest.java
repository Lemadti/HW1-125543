package pt.ua.deti.tqs.orbit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.support.TestData;

@DisplayName("Parcel — the aggregate that holds the lifecycle")
class ParcelTest {

    private final Station station = TestData.station(1, "ORB-AVR-001", 40.64, -8.65);
    private final Compartment m07 = TestData.compartment(7, station, "M-07", CompartmentSize.M);

    @Test
    @DisplayName("US2: registration records the first transition and the drop-off deadline")
    void registrationRecordsTheInitialTransitionAndDeadline() {
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "h");

        assertThat(p.getState()).isEqualTo(ParcelState.CREATED);
        assertThat(p.getDropOffDeadline()).isEqualTo(TestData.DROP_OFF_DEADLINE);
        assertThat(p.getHistory()).singleElement().satisfies(t -> {
            assertThat(t.getFromState()).isNull();
            assertThat(t.getToState()).isEqualTo(ParcelState.CREATED);
            assertThat(t.getActor()).isEqualTo(Actor.PARTNER);
            assertThat(t.getOccurredAt()).isEqualTo(TestData.T0);
        });
    }

    @Test
    @DisplayName("US5: every transition is recorded with its timestamp and actor")
    void everyTransitionIsRecordedWithTimestampAndActor() {
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "h");
        Instant t1 = TestData.T0.plusSeconds(60);
        Instant t2 = TestData.T0.plusSeconds(120);

        p.apply(ParcelEvent.COLLECT, Actor.COURIER, t1, "collected");
        p.apply(ParcelEvent.DEPOSIT, Actor.COURIER, t2, null);

        assertThat(p.getState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(p.getHistory()).extracting(ParcelTransition::getToState, ParcelTransition::getActor,
                        ParcelTransition::getOccurredAt)
                .containsExactly(
                        tuple(ParcelState.CREATED, Actor.PARTNER, TestData.T0),
                        tuple(ParcelState.ON_ITS_WAY, Actor.COURIER, t1),
                        tuple(ParcelState.READY_FOR_PICKUP, Actor.COURIER, t2));
    }

    @Test
    @DisplayName("State machine: a rejected event changes neither state nor history")
    void rejectedEventLeavesStateAndHistoryUntouched() {
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "h");

        assertThatThrownBy(() -> p.apply(ParcelEvent.PICK_UP, Actor.RECIPIENT, TestData.T0, null))
                .isInstanceOf(InvalidTransitionException.class);
        assertThat(p.getState()).isEqualTo(ParcelState.CREATED);
        assertThat(p.getHistory()).hasSize(1);
    }

    @Test
    @DisplayName("Pickup code: the deposit sets the pickup code and clears the drop-off code")
    void depositSetsPickupCodeAndClearsDropOffCode() {
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "drop");
        p.markDeposited(TestData.T0, TestData.T0.plus(Duration.ofDays(15)), "pick");
        assertThat(p.getDropOffCodeHash()).isNull();
        assertThat(p.getPickupCodeHash()).isEqualTo("pick");
        p.consumePickupCode();
        assertThat(p.getPickupCodeHash()).isNull();
    }

    @Test
    @DisplayName("Door doesn't open: a parcel can only move to a compartment of the same size")
    void relocationOnlyToSameSize() {
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "drop");
        Compartment l04 = TestData.compartment(4, station, "L-04", CompartmentSize.L);
        Compartment m11 = TestData.compartment(11, station, "M-11", CompartmentSize.M);

        assertThatThrownBy(() -> p.relocateTo(l04, "x")).isInstanceOf(IllegalArgumentException.class);
        p.relocateTo(m11, "new");
        assertThat(p.getCompartment()).isSameAs(m11);
        assertThat(p.getDropOffCodeHash()).isEqualTo("new");
    }

    @Test
    @DisplayName("Terminal: a session token stops working when it expires")
    void terminalSessionExpires() {
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "drop");
        p.openTerminalSession("tok", TestData.T0.plusSeconds(300));
        assertThat(p.hasTerminalSession("tok", TestData.T0.plusSeconds(299))).isTrue();
        assertThat(p.hasTerminalSession("tok", TestData.T0.plusSeconds(300))).isFalse();
        assertThat(p.hasTerminalSession("other", TestData.T0)).isFalse();
    }

    @Test
    @DisplayName("US5: public pages show the recipient's first name and initial only")
    void contactNameIsMaskedForPublicPages() {
        assertThat(new Contact("João Santos", "e", "p").maskedName()).isEqualTo("João S.");
        assertThat(new Contact("Madonna", "e", "p").maskedName()).isEqualTo("Madonna");
    }
}
