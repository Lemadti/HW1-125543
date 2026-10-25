package pt.ua.deti.tqs.orbit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.support.TestData;

class ParcelTest {

    private final Station station = TestData.station(1, "ORB-WAW-014", 52.23, 21.01);
    private final Compartment m07 = TestData.compartment(7, station, "M-07", CompartmentSize.M);

    @Test
    void registrationRecordsTheInitialTransitionAndDeadline() {
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "h");

        assertThat(p.getState()).isEqualTo(ParcelState.CREATED);
        assertThat(p.getDropOffDeadline()).isEqualTo(TestData.T0.plus(Duration.ofHours(48)));
        assertThat(p.getHistory()).singleElement().satisfies(t -> {
            assertThat(t.getFromState()).isNull();
            assertThat(t.getToState()).isEqualTo(ParcelState.CREATED);
            assertThat(t.getActor()).isEqualTo(Actor.PARTNER);
            assertThat(t.getOccurredAt()).isEqualTo(TestData.T0);
        });
    }

    @Test
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
                        org.assertj.core.groups.Tuple.tuple(ParcelState.CREATED, Actor.PARTNER, TestData.T0),
                        org.assertj.core.groups.Tuple.tuple(ParcelState.ON_ITS_WAY, Actor.COURIER, t1),
                        org.assertj.core.groups.Tuple.tuple(ParcelState.READY_FOR_PICKUP, Actor.COURIER, t2));
    }

    @Test
    void rejectedEventLeavesStateAndHistoryUntouched() {
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "h");

        assertThatThrownBy(() -> p.apply(ParcelEvent.PICK_UP, Actor.RECIPIENT, TestData.T0, null))
                .isInstanceOf(InvalidTransitionException.class);
        assertThat(p.getState()).isEqualTo(ParcelState.CREATED);
        assertThat(p.getHistory()).hasSize(1);
    }

    @Test
    void depositSetsPickupCodeAndClearsDropOffCode() {
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "drop");
        p.markDeposited(TestData.T0, TestData.T0.plus(Duration.ofDays(15)), "pick");
        assertThat(p.getDropOffCodeHash()).isNull();
        assertThat(p.getPickupCodeHash()).isEqualTo("pick");
        p.consumePickupCode();
        assertThat(p.getPickupCodeHash()).isNull();
    }

    @Test
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
    void terminalSessionExpires() {
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "drop");
        p.openTerminalSession("tok", TestData.T0.plusSeconds(300));
        assertThat(p.hasTerminalSession("tok", TestData.T0.plusSeconds(299))).isTrue();
        assertThat(p.hasTerminalSession("tok", TestData.T0.plusSeconds(300))).isFalse();
        assertThat(p.hasTerminalSession("other", TestData.T0)).isFalse();
    }

    @Test
    void contactNameIsMaskedForPublicPages() {
        assertThat(new Contact("Piotr Nowak", "e", "p").maskedName()).isEqualTo("Piotr N.");
        assertThat(new Contact("Madonna", "e", "p").maskedName()).isEqualTo("Madonna");
    }
}
