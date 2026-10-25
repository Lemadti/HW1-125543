package pt.ua.deti.tqs.orbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pt.ua.deti.tqs.orbit.adapter.hardware.FakeLockerHardware;
import pt.ua.deti.tqs.orbit.adapter.hardware.FakeLockerHardware.Fault;
import pt.ua.deti.tqs.orbit.config.MutableClock;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.Compartment;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.Incident;
import pt.ua.deti.tqs.orbit.domain.IncidentType;
import pt.ua.deti.tqs.orbit.domain.InvalidTransitionException;
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.domain.Parcel;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.domain.Station;
import pt.ua.deti.tqs.orbit.port.CompartmentRef;
import pt.ua.deti.tqs.orbit.port.DoorStatus;
import pt.ua.deti.tqs.orbit.port.Notification;
import pt.ua.deti.tqs.orbit.repository.CompartmentRepository;
import pt.ua.deti.tqs.orbit.repository.IncidentRepository;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;
import pt.ua.deti.tqs.orbit.repository.StationRepository;
import pt.ua.deti.tqs.orbit.service.view.TerminalResult;
import pt.ua.deti.tqs.orbit.support.RecordingNotifier;
import pt.ua.deti.tqs.orbit.support.TestData;
import pt.ua.deti.tqs.orbit.support.TestProperties;

/**
 * Terminal rules and hardware failure rules at unit level. Test doubles:
 * <ul>
 *   <li>{@link FakeLockerHardware} (fake, stateful, failure injection) for the hardware port;</li>
 *   <li>{@link RecordingNotifier} (fake) to read the codes that would be sent;</li>
 *   <li>{@link MutableClock} for time;</li>
 *   <li>Mockito mocks for the repositories (persistence is covered by the integration tests).</li>
 * </ul>
 */
class TerminalServiceTest {

    private static final String STATION = "ORB-AVR-001";
    private static final String DROP_PIN = "123456";

    private final MutableClock clock = MutableClock.fixedAt(TestData.T0);
    private final FakeLockerHardware hardware = new FakeLockerHardware(false);
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final StationRepository stations = mock(StationRepository.class);
    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final CompartmentRepository compartments = mock(CompartmentRepository.class);
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final CodeService codes = new CodeService(new SecureRandom(), "test-secret", 6);

    private final Station station = TestData.station(1, STATION, 52.23, 21.01);
    private final Compartment m07 = TestData.compartment(7, station, "M-07", CompartmentSize.M);
    private final Compartment m11 = TestData.compartment(11, station, "M-11", CompartmentSize.M);
    private final CompartmentRef m07Ref = new CompartmentRef(STATION, "M-07");
    private TerminalService terminal;
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        TransitionRecorder transitions = new TransitionRecorder(clock);
        terminal = new TerminalService(stations, parcels, hardware, notifier, codes, new PinIssuer(codes, parcels),
                new AttemptGuard(clock, 3, Duration.ofHours(1)), new CompartmentAllocator(compartments, parcels, transitions),
                transitions, new IncidentReporter(incidents, notifier, clock), clock, TestProperties.defaults());

        parcel = TestData.parcel(10, "ORB-261024-0714", m07, codes.hash(DROP_PIN));
        when(stations.findByCode(STATION)).thenReturn(Optional.of(station));
        when(stations.findByCodeForUpdate(STATION)).thenReturn(Optional.of(station));
        when(parcels.findByStationAndDropOffCodeHashAndStateIn(eq(station), anyString(), any())).thenReturn(Optional.empty());
        when(parcels.findByStationAndDropOffCodeHashAndStateIn(eq(station), eq(codes.hash(DROP_PIN)), any()))
                .thenReturn(Optional.of(parcel));
        when(parcels.findByStationAndPickupCodeHashAndStateIn(eq(station), anyString(), any())).thenReturn(Optional.empty());
        when(parcels.findByStationAndTerminalSession(eq(station), anyString()))
                .thenAnswer(inv -> Optional.of(parcel).filter(p -> p.hasTerminalSession(inv.getArgument(1), clock.instant())));
        when(parcels.findByCompartmentAndStateIn(any(), any())).thenReturn(List.of());
        when(compartments.findAllocatable(station, CompartmentSize.M)).thenReturn(List.of(m11));
        when(compartments.findByStationAndCode(station, "M-11")).thenReturn(Optional.of(m11));
    }

    private void collected() {
        parcel.apply(ParcelEvent.COLLECT, Actor.COURIER, clock.instant(), null);
    }

    private String pickupCodeAfterDeposit() {
        collected();
        TerminalResult opened = terminal.startDropOff(STATION, DROP_PIN);
        hardware.closeDoor(m07Ref);
        terminal.confirmDropOff(STATION, opened.sessionToken());
        String pin = notifier.lastCode(Notification.Type.PICKUP_CODE).orElseThrow();
        when(parcels.findByStationAndPickupCodeHashAndStateIn(eq(station), eq(codes.hash(pin)), any()))
                .thenReturn(Optional.of(parcel));
        return pin;
    }

    @Nested
    @DisplayName("drop-off (UC3)")
    class DropOff {

        @Test
        void happyPathOpensDoorThenDepositsWhenTheDoorIsClosed() {
            collected();
            TerminalResult opened = terminal.startDropOff(STATION, DROP_PIN);

            assertThat(opened.outcome()).isEqualTo(TerminalService.DOOR_OPENED);
            assertThat(opened.compartmentCode()).isEqualTo("M-07");
            assertThat(hardware.doorStatus(m07Ref)).isEqualTo(DoorStatus.OPEN);
            assertThat(parcel.getState()).isEqualTo(ParcelState.ON_ITS_WAY);

            hardware.closeDoor(m07Ref);
            TerminalResult done = terminal.confirmDropOff(STATION, opened.sessionToken());

            assertThat(done.outcome()).isEqualTo(TerminalService.DEPOSITED);
            assertThat(parcel.getState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
            assertThat(parcel.getPickupDeadline()).isEqualTo(TestData.T0.plus(Duration.ofDays(15)));
            assertThat(parcel.getDropOffCodeHash()).isNull();
            String pickup = notifier.lastCode(Notification.Type.PICKUP_CODE).orElseThrow();
            assertThat(pickup).matches("\\d{6}").isNotEqualTo(DROP_PIN);
            assertThat(parcel.getPickupCodeHash()).isEqualTo(codes.hash(pickup));
            assertThat(notifier.ofType(Notification.Type.PICKUP_CODE).get(0).toEmail()).isEqualTo("joao@example.com");
        }

        @Test
        void parcelNotYetCollectedIsRejectedWithoutOpeningAnyDoor() {
            assertThatThrownBy(() -> terminal.startDropOff(STATION, DROP_PIN))
                    .isInstanceOf(InvalidTransitionException.class);
            assertThat(hardware.calls()).isEmpty();
        }

        @Test
        void doorThatDoesNotOpenPutsCompartmentOutOfServiceAndOffersReplacement() {
            collected();
            hardware.inject(m07Ref, Fault.DOOR_WONT_OPEN);

            TerminalResult r = terminal.startDropOff(STATION, DROP_PIN);

            assertThat(r.outcome()).isEqualTo(TerminalService.DOOR_DID_NOT_OPEN);
            assertThat(r.replacementAvailable()).isTrue();
            assertThat(r.sessionToken()).isNotNull();
            assertThat(m07.isInService()).isFalse();
            assertThat(parcel.getState()).isEqualTo(ParcelState.ON_ITS_WAY);
            ArgumentCaptor<Incident> incident = ArgumentCaptor.forClass(Incident.class);
            verify(incidents).save(incident.capture());
            assertThat(incident.getValue().getType()).isEqualTo(IncidentType.DOOR_DID_NOT_OPEN);
            assertThat(notifier.ofType(Notification.Type.OPERATOR_ALERT)).hasSize(1);
        }

        @Test
        void replacementMovesTheParcelAndSendsANewCodeThatOpensTheNewCompartment() {
            collected();
            hardware.inject(m07Ref, Fault.DOOR_WONT_OPEN);
            TerminalResult failed = terminal.startDropOff(STATION, DROP_PIN);

            TerminalResult r = terminal.requestReplacement(STATION, failed.sessionToken());

            assertThat(r.outcome()).isEqualTo(TerminalService.REPLACEMENT_ISSUED);
            assertThat(parcel.getCompartment()).isSameAs(m11);
            String newPin = notifier.lastCode(Notification.Type.REPLACEMENT_DROP_OFF_CODE).orElseThrow();
            assertThat(parcel.getDropOffCodeHash()).isEqualTo(codes.hash(newPin)).isNotEqualTo(codes.hash(DROP_PIN));
            assertThat(notifier.ofType(Notification.Type.REPLACEMENT_DROP_OFF_CODE).get(0).toEmail())
                    .isEqualTo("ana@example.com");

            when(parcels.findByStationAndDropOffCodeHashAndStateIn(eq(station), eq(codes.hash(newPin)), any()))
                    .thenReturn(Optional.of(parcel));
            TerminalResult reopened = terminal.startDropOff(STATION, newPin);
            assertThat(reopened.outcome()).isEqualTo(TerminalService.DOOR_OPENED);
            assertThat(hardware.doorStatus(new CompartmentRef(STATION, "M-11"))).isEqualTo(DoorStatus.OPEN);
        }

        @Test
        void noReplacementWhenNoOtherCompartmentOfTheSameSizeIsFree() {
            collected();
            hardware.inject(m07Ref, Fault.DOOR_WONT_OPEN);
            when(compartments.findAllocatable(station, CompartmentSize.M)).thenReturn(List.of());
            TerminalResult failed = terminal.startDropOff(STATION, DROP_PIN);
            assertThat(failed.replacementAvailable()).isFalse();

            TerminalResult r = terminal.requestReplacement(STATION, failed.sessionToken());

            assertThat(r.outcome()).isEqualTo(TerminalService.NO_REPLACEMENT_AVAILABLE);
            assertThat(parcel.getCompartment()).isSameAs(m07);
            assertThat(parcel.getState()).isEqualTo(ParcelState.ON_ITS_WAY);
        }

        @Test
        void replacementWithoutAFailureIsRejected() {
            collected();
            TerminalResult opened = terminal.startDropOff(STATION, DROP_PIN);
            assertThatThrownBy(() -> terminal.requestReplacement(STATION, opened.sessionToken()))
                    .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("NO_FAILURE_REPORTED"));
        }

        @Test
        void doorThatDoesNotCloseKeepsTheParcelOnItsWayAndReportsTheFailure() {
            collected();
            hardware.inject(m07Ref, Fault.DOOR_WONT_CLOSE);
            TerminalResult opened = terminal.startDropOff(STATION, DROP_PIN);
            hardware.closeDoor(m07Ref); // the user tries, but the door stays open

            TerminalResult r = terminal.confirmDropOff(STATION, opened.sessionToken());

            assertThat(r.outcome()).isEqualTo(TerminalService.DOOR_DID_NOT_CLOSE);
            assertThat(r.replacementAvailable()).isTrue();
            assertThat(parcel.getState()).isEqualTo(ParcelState.ON_ITS_WAY);
            assertThat(m07.isInService()).isFalse();
            assertThat(notifier.ofType(Notification.Type.PICKUP_CODE)).isEmpty();
        }

        @Test
        void unreachableHardwareIsReportedWithoutChangingTheCompartment() {
            collected();
            hardware.setStationUnreachable(true);

            assertThatThrownBy(() -> terminal.startDropOff(STATION, DROP_PIN))
                    .isInstanceOfSatisfying(OrbitException.class, e -> {
                        assertThat(e.getKind()).isEqualTo(OrbitException.Kind.UNAVAILABLE);
                        assertThat(e.getCode()).isEqualTo("HARDWARE_UNAVAILABLE");
                    });
            assertThat(m07.isInService()).isTrue();
            assertThat(parcel.getState()).isEqualTo(ParcelState.ON_ITS_WAY);
        }

        @Test
        void expiredParcelFlaggedToReturnIsRemovedOnTheNextDropOff() {
            Parcel old = TestData.parcel(9, "ORB-261001-0001", m07, null);
            old.apply(ParcelEvent.COLLECT, Actor.COURIER, TestData.T0, null);
            old.apply(ParcelEvent.DEPOSIT, Actor.COURIER, TestData.T0, null);
            old.apply(ParcelEvent.PICKUP_DEADLINE_PASSED, Actor.SYSTEM, TestData.T0, null);
            old.apply(ParcelEvent.MARK_TO_RETURN, Actor.SYSTEM, TestData.T0, null);
            when(parcels.findByCompartmentAndStateIn(m07, List.of(ParcelState.TO_RETURN))).thenReturn(List.of(old));
            collected();

            TerminalResult opened = terminal.startDropOff(STATION, DROP_PIN);
            hardware.closeDoor(m07Ref);
            terminal.confirmDropOff(STATION, opened.sessionToken());

            assertThat(old.getState()).isEqualTo(ParcelState.RETURNED);
            assertThat(old.getHistory().get(old.getHistory().size() - 1).getActor()).isEqualTo(Actor.COURIER);
            assertThat(parcel.getState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        }

        @Test
        void expiredSessionCannotConfirm() {
            collected();
            TerminalResult opened = terminal.startDropOff(STATION, DROP_PIN);
            clock.advance(Duration.ofMinutes(6));
            assertThatThrownBy(() -> terminal.confirmDropOff(STATION, opened.sessionToken()))
                    .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("SESSION_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("codes and guessing protection")
    class Codes {

        @Test
        void wrongCodeReportsRemainingAttemptsAndTheThirdBlocksTheTerminal() {
            assertThatThrownBy(() -> terminal.startPickup(STATION, "000000"))
                    .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getDetails()).containsEntry("remainingAttempts", 2));
            assertThatThrownBy(() -> terminal.startPickup(STATION, "000001"))
                    .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getDetails()).containsEntry("remainingAttempts", 1));
            assertThatThrownBy(() -> terminal.startPickup(STATION, "000002"))
                    .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getKind()).isEqualTo(OrbitException.Kind.LOCKED));
        }

        @Test
        void blockedTerminalRejectsEvenTheCorrectCodeUntilTheLockoutEnds() {
            String pin = pickupCodeAfterDeposit();
            for (String wrong : List.of("000000", "000001")) {
                assertThatThrownBy(() -> terminal.startPickup(STATION, wrong)).isInstanceOf(OrbitException.class);
            }
            assertThatThrownBy(() -> terminal.startPickup(STATION, "000002")).isInstanceOf(OrbitException.class);

            assertThatThrownBy(() -> terminal.startPickup(STATION, pin))
                    .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getKind()).isEqualTo(OrbitException.Kind.LOCKED));
            clock.advance(Duration.ofHours(1));
            assertThat(terminal.startPickup(STATION, pin).outcome()).isEqualTo(TerminalService.DOOR_OPENED);
        }

        @Test
        void malformedCodeCountsAsAWrongAttempt() {
            assertThatThrownBy(() -> terminal.startDropOff(STATION, "12"))
                    .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_CODE"));
        }
    }

    @Nested
    @DisplayName("pickup (UC4)")
    class Pickup {

        @Test
        void validCodeOpensTheDoorAndThePickupCodeIsSingleUse() {
            String pin = pickupCodeAfterDeposit();

            TerminalResult r = terminal.startPickup(STATION, pin);

            assertThat(r.outcome()).isEqualTo(TerminalService.DOOR_OPENED);
            assertThat(parcel.getState()).isEqualTo(ParcelState.PICKED_UP);
            assertThat(parcel.getPickupCodeHash()).isNull();
            assertThat(parcel.getHistory().get(parcel.getHistory().size() - 1).getActor()).isEqualTo(Actor.RECIPIENT);

            hardware.closeDoor(m07Ref);
            assertThat(terminal.confirmPickup(STATION, r.sessionToken()).outcome()).isEqualTo(TerminalService.COMPLETED);
        }

        @Test
        void doorThatDoesNotOpenKeepsTheCodeValidAndTheParcelReady() {
            String pin = pickupCodeAfterDeposit();
            hardware.inject(m07Ref, Fault.DOOR_WONT_OPEN);

            TerminalResult r = terminal.startPickup(STATION, pin);

            assertThat(r.outcome()).isEqualTo(TerminalService.DOOR_DID_NOT_OPEN);
            assertThat(r.message()).contains("+351 234 100 200");
            assertThat(parcel.getState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
            assertThat(parcel.getPickupCodeHash()).isEqualTo(codes.hash(pin));
            assertThat(m07.isInService()).isFalse();
        }

        @Test
        void doorThatDoesNotCloseAfterPickupIsReportedButThePickupStands() {
            String pin = pickupCodeAfterDeposit();
            hardware.inject(m07Ref, Fault.DOOR_WONT_CLOSE);
            TerminalResult r = terminal.startPickup(STATION, pin);

            TerminalResult closed = terminal.confirmPickup(STATION, r.sessionToken());

            assertThat(closed.outcome()).isEqualTo(TerminalService.DOOR_DID_NOT_CLOSE);
            assertThat(parcel.getState()).isEqualTo(ParcelState.PICKED_UP);
            assertThat(m07.isInService()).isFalse();
        }

        @Test
        void expiredParcelCanStillBeCollected() {
            String pin = pickupCodeAfterDeposit();
            parcel.apply(ParcelEvent.PICKUP_DEADLINE_PASSED, Actor.SYSTEM, clock.instant(), null);

            assertThat(terminal.startPickup(STATION, pin).state()).isEqualTo(ParcelState.PICKED_UP);
        }
    }
}
