package pt.ua.deti.tqs.orbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/** Time-based rules without sleeping: the repository is asked "deadline <= now" with the mutable clock's now. */
class DeadlineServiceTest {

    private final MutableClock clock = MutableClock.fixedAt(TestData.T0);
    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final RecordingNotifier notifier = new RecordingNotifier();
    private DeadlineService deadlines;
    private Parcel created;
    private Parcel ready;

    @BeforeEach
    void setUp() {
        deadlines = new DeadlineService(parcels, new TransitionRecorder(clock), notifier, clock,
                mock(PlatformTransactionManager.class));
        Compartment c = TestData.compartment(1, TestData.station(1, "S", 52, 21), "M-03", CompartmentSize.M);
        created = TestData.parcel(1, "ORB-261024-0001", c, "h");
        ready = TestData.parcel(2, "ORB-261024-0002", c, "h");
        ready.apply(ParcelEvent.COLLECT, Actor.COURIER, TestData.T0, null);
        ready.apply(ParcelEvent.DEPOSIT, Actor.COURIER, TestData.T0, null);
        ready.markDeposited(TestData.T0, TestData.T0.plus(Duration.ofDays(15)), "p");
        when(parcels.findById(1L)).thenReturn(Optional.of(created));
        when(parcels.findById(2L)).thenReturn(Optional.of(ready));
        // the fake "query": parcels whose deadline is <= the given instant
        when(parcels.findByStateAndDropOffDeadlineLessThanEqual(eq(ParcelState.CREATED), any())).thenAnswer(inv ->
                created.getState() == ParcelState.CREATED && !created.getDropOffDeadline().isAfter(inv.<Instant>getArgument(1))
                        ? List.of(created) : List.of());
        when(parcels.findByStateAndPickupDeadlineLessThanEqual(eq(ParcelState.READY_FOR_PICKUP), any())).thenAnswer(inv ->
                ready.getState() == ParcelState.READY_FOR_PICKUP && !ready.getPickupDeadline().isAfter(inv.<Instant>getArgument(1))
                        ? List.of(ready) : List.of());
    }

    @Test
    void nothingHappensBeforeTheDeadlines() {
        clock.advance(Duration.ofHours(47).plusMinutes(59));
        assertThat(deadlines.processDeadlines()).isEqualTo(new DeadlineService.Report(0, 0));
        assertThat(created.getState()).isEqualTo(ParcelState.CREATED);
    }

    @Test
    void leaseEndCancelsUncollectedParcelBySystem() {
        clock.advance(Duration.ofHours(48));
        assertThat(deadlines.processDeadlines()).isEqualTo(new DeadlineService.Report(1, 0));
        assertThat(created.getState()).isEqualTo(ParcelState.CANCELLED);
        ParcelTransition last = created.getHistory().get(created.getHistory().size() - 1);
        assertThat(last.getActor()).isEqualTo(Actor.SYSTEM);
        assertThat(last.getEvent()).isEqualTo(ParcelEvent.DROP_OFF_DEADLINE_PASSED);
        assertThat(last.getOccurredAt()).isEqualTo(TestData.T0.plus(Duration.ofHours(48)));
        assertThat(notifier.ofType(Notification.Type.PARCEL_CANCELLED)).hasSize(1);
    }

    @Test
    void pickupWindowEndExpiresTheParcelAndIsIdempotent() {
        clock.advance(Duration.ofDays(15));
        DeadlineService.Report first = deadlines.processDeadlines();
        assertThat(first.expired()).isEqualTo(1);
        assertThat(ready.getState()).isEqualTo(ParcelState.EXPIRED);
        assertThat(deadlines.processDeadlines()).isEqualTo(new DeadlineService.Report(0, 0));
        assertThat(notifier.ofType(Notification.Type.PARCEL_EXPIRED)).hasSize(1);
    }
}
