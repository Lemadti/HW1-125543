package pt.ua.deti.tqs.orbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.orbit.config.MutableClock;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.Compartment;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.CompartmentStatus;
import pt.ua.deti.tqs.orbit.domain.Parcel;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.domain.ParcelTransition;
import pt.ua.deti.tqs.orbit.domain.Station;
import pt.ua.deti.tqs.orbit.port.Notification;
import pt.ua.deti.tqs.orbit.repository.CompartmentRepository;
import pt.ua.deti.tqs.orbit.repository.IncidentRepository;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;
import pt.ua.deti.tqs.orbit.repository.StationRepository;
import pt.ua.deti.tqs.orbit.support.RecordingNotifier;
import pt.ua.deti.tqs.orbit.support.TestData;
import pt.ua.deti.tqs.orbit.support.TestProperties;

/**
 * Unit tests for the operator use cases (US6) of {@link OperatorService}.
 *
 * <p>No Spring context is started: the service is built with {@code new}, the repositories are Mockito mocks and the
 * clock is fixed. Where a collaborator is not part of the rule under test (allocator, PIN issuer, incident reporter in
 * the removal test) it is a mock too, and {@code verifyNoInteractions} proves it was not needed.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OperatorService — supervising a station (repositories mocked)")
class OperatorServiceTest {

    @Mock
    private StationRepository stations;
    @Mock
    private CompartmentRepository compartments;
    @Mock
    private ParcelRepository parcels;
    @Mock
    private IncidentRepository incidents;

    private final Station station = TestData.station(1, "ORB-AVR-001", 40.64, -8.65);
    private final Compartment m07 = TestData.compartment(7, station, "M-07", CompartmentSize.M);
    private final RecordingNotifier notifier = new RecordingNotifier();

    @Test
    @DisplayName("US6: out of service moves the reserved parcel to another compartment of the same size")
    void puttingTheParcelsCompartmentOutOfServiceMovesItToAnotherOfTheSameSize() {
        // Arrange: a reserved parcel in M-07, M-11 free; real services, mocked repositories
        Compartment m11 = TestData.compartment(11, station, "M-11", CompartmentSize.M);
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "old-drop-off-hash");
        when(stations.findByCodeForUpdate("ORB-AVR-001")).thenReturn(Optional.of(station));
        when(compartments.findByStationAndCode(station, "M-07")).thenReturn(Optional.of(m07));
        when(compartments.findByStationAndCode(station, "M-11")).thenReturn(Optional.of(m11));
        when(compartments.findAllocatable(station, CompartmentSize.M)).thenReturn(List.of(m11));
        when(parcels.findByCompartmentAndStateIn(any(), any())).thenReturn(List.of());
        when(parcels.findByCompartmentAndStateIn(eq(m07), any())).thenReturn(List.of(p));
        MutableClock clock = MutableClock.fixedAt(TestData.T0);
        TransitionRecorder transitions = new TransitionRecorder(clock);
        CodeService codes = new CodeService(TestProperties.defaults());
        OperatorService operator = new OperatorService(stations, compartments, parcels, incidents,
                new CompartmentAllocator(compartments, parcels, transitions), new PinIssuer(codes, parcels), transitions,
                new IncidentReporter(incidents, notifier, clock), notifier);

        // Act
        operator.setCompartmentStatus("ORB-AVR-001", "M-07", CompartmentStatus.OUT_OF_SERVICE, "Broken hinge");

        // Assert
        assertThat(m07.isInService()).isFalse();
        assertThat(p.getCompartment()).isSameAs(m11);
        assertThat(p.getDropOffCodeHash()).isNotEqualTo("old-drop-off-hash");
        assertThat(notifier.lastCode(Notification.Type.REPLACEMENT_DROP_OFF_CODE)).get()
                .satisfies(pin -> assertThat(p.getDropOffCodeHash()).isEqualTo(codes.hash(pin)));
    }

    @Test
    @DisplayName("US6: removing an expired parcel returns it and cancels the pickup code")
    void operatorRegisteringTheRemovalOfAnExpiredParcelReturnsItAndInvalidatesThePickupCode(
            @Mock CompartmentAllocator allocator, @Mock PinIssuer pins, @Mock IncidentReporter reporter) {
        // Arrange: an expired parcel still holding its pickup code
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "drop");
        p.apply(ParcelEvent.COLLECT, Actor.COURIER, TestData.T0, null);
        p.apply(ParcelEvent.DEPOSIT, Actor.COURIER, TestData.T0, null);
        p.markDeposited(TestData.T0, TestData.T0.plus(Duration.ofDays(15)), "pickup-hash");
        p.apply(ParcelEvent.PICKUP_DEADLINE_PASSED, Actor.SYSTEM, TestData.T0.plus(Duration.ofDays(15)), null);
        when(parcels.findByTrackingCode("ORB-261024-0001")).thenReturn(Optional.of(p));
        TransitionRecorder transitions = new TransitionRecorder(MutableClock.fixedAt(TestData.T0.plus(Duration.ofDays(16))));
        OperatorService operator = new OperatorService(stations, compartments, parcels, incidents, allocator, pins,
                transitions, reporter, notifier);

        // Act
        operator.registerRemoval("ORB-261024-0001");

        // Assert
        assertThat(p.getState()).isEqualTo(ParcelState.RETURNED);
        assertThat(p.getHistory()).extracting(ParcelTransition::getToState, ParcelTransition::getActor)
                .endsWith(tuple(ParcelState.TO_RETURN, Actor.OPERATOR),
                        tuple(ParcelState.RETURNED, Actor.OPERATOR));
        assertThat(p.getPickupCodeHash()).isNull();
        assertThat(notifier.sent()).singleElement()
                .satisfies(n -> assertThat(n.toEmail()).isEqualTo(TestData.SENDER.getEmail()));
        verifyNoInteractions(allocator, pins, reporter);
    }
}
