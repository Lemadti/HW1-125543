package pt.ua.deti.tqs.orbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import pt.ua.deti.tqs.orbit.config.MutableClock;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.Compartment;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.Parcel;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.domain.ParcelTransition;
import pt.ua.deti.tqs.orbit.port.Notification;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;
import pt.ua.deti.tqs.orbit.support.RecordingNotifier;
import pt.ua.deti.tqs.orbit.support.TestData;

/**
 * Unit tests for the time-based rules of {@link DeadlineService}.
 *
 * <p>No Spring context and no sleeping: the clock is a {@link MutableClock} moved by each test. The repository is a
 * Mockito mock that answers the two deadline queries like the database would ("deadline before now", with the
 * clock's now); the notifier is a recording fake, so the oracle is what would have been sent.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeadlineService — time-based rules (clock moved, nothing sleeps)")
class DeadlineServiceTest {

    private final MutableClock clock = MutableClock.fixedAt(TestData.T0);
    @Mock
    private ParcelRepository parcels;
    @Mock
    private PlatformTransactionManager transactionManager;
    private final RecordingNotifier notifier = new RecordingNotifier();
    private DeadlineService deadlines;
    private Parcel created;
    private Parcel ready;

    @BeforeEach
    void setUp() {
        deadlines = new DeadlineService(parcels, new TransitionRecorder(clock), notifier, clock, transactionManager);
        Compartment c = TestData.compartment(1, TestData.station(1, "S", 52, 21), "M-03", CompartmentSize.M);
        created = TestData.parcel(1, "ORB-261024-0001", c, "h");
        ready = TestData.parcel(2, "ORB-261024-0002", c, "h");
        ready.apply(ParcelEvent.COLLECT, Actor.COURIER, TestData.T0, null);
        ready.apply(ParcelEvent.DEPOSIT, Actor.COURIER, TestData.T0, null);
        ready.markDeposited(TestData.T0, TestData.T0.plus(Duration.ofDays(15)), "p");
        // The stubs below make the mock behave as a tiny in-memory repository (a fake), shared by every test; not
        // every test reaches every query, so they are lenient on purpose. They are not expectations.
        lenient().when(parcels.findById(1L)).thenReturn(Optional.of(created));
        lenient().when(parcels.findById(2L)).thenReturn(Optional.of(ready));
        lenient().when(parcels.findByStateInAndDropOffDeadlineBefore(any(), any())).thenAnswer(inv ->
                inv.<Collection<ParcelState>>getArgument(0).contains(created.getState())
                        && created.getDropOffDeadline().isBefore(inv.<Instant>getArgument(1))
                        ? List.of(created) : List.of());
        lenient().when(parcels.findByStateAndPickupDeadlineLessThanEqual(eq(ParcelState.READY_FOR_PICKUP), any())).thenAnswer(inv ->
                ready.getState() == ParcelState.READY_FOR_PICKUP && !ready.getPickupDeadline().isAfter(inv.<Instant>getArgument(1))
                        ? List.of(ready) : List.of());
    }

    @Test
    @DisplayName("Drop-off deadline: up to the deadline nothing is cancelled")
    void nothingHappensUpToTheDeadlines() {
        clock.setFixed(TestData.DROP_OFF_DEADLINE);
        assertThat(deadlines.processDeadlines()).isEqualTo(new DeadlineService.Report(0, 0));
        assertThat(created.getState()).isEqualTo(ParcelState.CREATED);
    }

    @Test
    @DisplayName("Drop-off deadline: an uncollected parcel is cancelled by SYSTEM")
    void passedDropOffDeadlineCancelsUncollectedParcelBySystem() {
        clock.setFixed(TestData.DROP_OFF_DEADLINE.plusSeconds(1));
        assertThat(deadlines.processDeadlines()).isEqualTo(new DeadlineService.Report(1, 0));
        assertThat(created.getState()).isEqualTo(ParcelState.CANCELLED);
        ParcelTransition last = created.getHistory().get(created.getHistory().size() - 1);
        assertThat(last.getActor()).isEqualTo(Actor.SYSTEM);
        assertThat(last.getEvent()).isEqualTo(ParcelEvent.DROP_OFF_DEADLINE_PASSED);
        assertThat(last.getOccurredAt()).isEqualTo(TestData.DROP_OFF_DEADLINE.plusSeconds(1));
        assertThat(notifier.ofType(Notification.Type.PARCEL_CANCELLED)).hasSize(1);
    }

    @Test
    @DisplayName("Drop-off deadline: a collected parcel not dropped off is cancelled")
    void passedDropOffDeadlineCancelsACollectedParcelNotDroppedOff() {
        created.apply(ParcelEvent.COLLECT, Actor.COURIER, TestData.T0, null);
        clock.setFixed(TestData.DROP_OFF_DEADLINE.plusSeconds(1));

        assertThat(deadlines.processDeadlines().cancelled()).isEqualTo(1);

        assertThat(created.getState()).isEqualTo(ParcelState.CANCELLED);
        assertThat(notifier.ofType(Notification.Type.PARCEL_CANCELLED)).singleElement()
                .satisfies(n -> assertThat(n.body()).contains("The courier will return the parcel to you."));
    }

    @Test
    @DisplayName("Drop-off deadline: a parcel with an open door at the terminal waits for the next run")
    void parcelWhoseDoorIsOpenAtTheTerminalIsLeftForTheNextRun() {
        created.apply(ParcelEvent.COLLECT, Actor.COURIER, TestData.T0, null);
        clock.setFixed(TestData.DROP_OFF_DEADLINE.plusSeconds(1));
        created.openTerminalSession("token", clock.instant().plus(Duration.ofMinutes(5)));

        assertThat(deadlines.processDeadlines().cancelled()).isZero();
        assertThat(created.getState()).isEqualTo(ParcelState.ON_ITS_WAY);

        clock.advance(Duration.ofMinutes(5));
        assertThat(deadlines.processDeadlines().cancelled()).isEqualTo(1);
    }

    @Test
    @DisplayName("Pickup deadline: after 15 days the parcel EXPIRES, exactly once")
    void pickupWindowEndExpiresTheParcelAndIsIdempotent() {
        clock.advance(Duration.ofDays(15));
        DeadlineService.Report first = deadlines.processDeadlines();
        assertThat(first.expired()).isEqualTo(1);
        assertThat(ready.getState()).isEqualTo(ParcelState.EXPIRED);
        assertThat(deadlines.processDeadlines()).isEqualTo(new DeadlineService.Report(0, 0));
        assertThat(notifier.ofType(Notification.Type.PARCEL_EXPIRED)).hasSize(1);
    }
}
