package pt.ua.deti.tqs.orbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
 * No Spring context is started: the service is built with {@code new} in {@code setUp}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TerminalService — the locker terminal (US3, US4) and hardware failures")
class TerminalServiceTest {

    private static final String STATION = "ORB-AVR-001";
    private static final String DROP_PIN = "123456";

    private final MutableClock clock = MutableClock.fixedAt(TestData.T0);
    private final FakeLockerHardware hardware = new FakeLockerHardware(false);
    private final RecordingNotifier notifier = new RecordingNotifier();
    @Mock
    private StationRepository stations;
    @Mock
    private ParcelRepository parcels;
    @Mock
    private CompartmentRepository compartments;
    @Mock
    private IncidentRepository incidents;
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
        // The stubs below make the repository mocks behave as a tiny in-memory database holding one parcel (a fake
        // shared by every test). Not every test reaches every query, so they are lenient on purpose: they are
        // answers, not expectations. Interactions that matter are checked with verify(...) in the tests.
        lenient().when(stations.findByCode(STATION)).thenReturn(Optional.of(station));
        lenient().when(stations.findByCodeForUpdate(STATION)).thenReturn(Optional.of(station));
        lenient().when(parcels.findByStationAndDropOffCodeHashAndStateIn(eq(station), anyString(), any())).thenReturn(Optional.empty());
        lenient().when(parcels.findByStationAndDropOffCodeHashAndStateIn(eq(station), eq(codes.hash(DROP_PIN)), any()))
                .thenReturn(Optional.of(parcel));
        lenient().when(parcels.findByStationAndPickupCodeHashAndStateIn(eq(station), anyString(), any())).thenReturn(Optional.empty());
        lenient().when(parcels.findByStationAndTerminalSession(eq(station), anyString()))
                .thenAnswer(inv -> Optional.of(parcel).filter(p -> p.hasTerminalSession(inv.getArgument(1), clock.instant())));
        lenient().when(parcels.findByCompartmentAndStateIn(any(), any())).thenReturn(List.of());
        lenient().when(compartments.findAllocatable(station, CompartmentSize.M)).thenReturn(List.of(m11));
        lenient().when(compartments.findByStationAndCode(station, "M-11")).thenReturn(Optional.of(m11));
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
        @DisplayName("US3: the door opens; closing it deposits the parcel and sends the pickup code")
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
        @DisplayName("US3: a parcel not yet collected is refused without opening any door")
        void parcelNotYetCollectedIsRejectedWithoutOpeningAnyDoor() {
            assertThatThrownBy(() -> terminal.startDropOff(STATION, DROP_PIN))
                    .isInstanceOf(InvalidTransitionException.class);
            assertThat(hardware.calls()).isEmpty();
            verifyNoInteractions(incidents);
            assertThat(notifier.sent()).isEmpty();
        }

        @Test
        @DisplayName("Door doesn't open: out of service, incident saved, replacement offered")
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
        @DisplayName("Door doesn't open: the replacement code opens the new compartment")
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
        @DisplayName("Door doesn't open: no replacement when no compartment of that size is free")
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
        @DisplayName("Door doesn't open: a replacement without a failure is refused")
        void replacementWithoutAFailureIsRejected() {
            collected();
            TerminalResult opened = terminal.startDropOff(STATION, DROP_PIN);
            assertThatThrownBy(() -> terminal.requestReplacement(STATION, opened.sessionToken()))
                    .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("NO_FAILURE_REPORTED"));
        }

        @Test
        @DisplayName("Door doesn't close: the parcel stays ON_ITS_WAY and no pickup code is sent")
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
        @DisplayName("Interface doesn't respond: HARDWARE_UNAVAILABLE and the compartment stays in service")
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
        @DisplayName("Pickup deadline: a TO_RETURN parcel is RETURNED by the courier on the next drop-off")
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
        @DisplayName("Terminal: an expired session cannot confirm the door")
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
        @DisplayName("Codes: wrong codes report the attempts left and the third blocks the terminal")
        void wrongCodeReportsRemainingAttemptsAndTheThirdBlocksTheTerminal() {
            assertThatThrownBy(() -> terminal.startPickup(STATION, "000000"))
                    .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getDetails()).containsEntry("remainingAttempts", 2));
            assertThatThrownBy(() -> terminal.startPickup(STATION, "000001"))
                    .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getDetails()).containsEntry("remainingAttempts", 1));
            assertThatThrownBy(() -> terminal.startPickup(STATION, "000002"))
                    .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getKind()).isEqualTo(OrbitException.Kind.LOCKED));
        }

        @Test
        @DisplayName("Codes: while blocked even the correct code is refused, for one hour")
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
        @DisplayName("Codes: a malformed code counts as a wrong attempt")
        void malformedCodeCountsAsAWrongAttempt() {
            assertThatThrownBy(() -> terminal.startDropOff(STATION, "12"))
                    .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_CODE"));
        }
    }

    @Nested
    @DisplayName("pickup (UC4)")
    class Pickup {

        @Test
        @DisplayName("US4 / NFR6: a valid code opens the door and is single use")
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
        @DisplayName("Door doesn't open: at pickup the code stays valid and the parcel READY")
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
        @DisplayName("Door doesn't close: after pickup it is reported, the pickup stands")
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
        @DisplayName("Pickup deadline: an EXPIRED parcel can still be collected")
        void expiredParcelCanStillBeCollected() {
            String pin = pickupCodeAfterDeposit();
            parcel.apply(ParcelEvent.PICKUP_DEADLINE_PASSED, Actor.SYSTEM, clock.instant(), null);

            assertThat(terminal.startPickup(STATION, pin).state()).isEqualTo(ParcelState.PICKED_UP);
        }
    }
}
