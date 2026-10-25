package pt.ua.deti.tqs.orbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.config.MutableClock;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.Compartment;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.InvalidTransitionException;
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.domain.Parcel;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.domain.Station;
import pt.ua.deti.tqs.orbit.port.Notification;
import pt.ua.deti.tqs.orbit.repository.CompartmentRepository;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;
import pt.ua.deti.tqs.orbit.repository.StationRepository;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;
import pt.ua.deti.tqs.orbit.support.RecordingNotifier;
import pt.ua.deti.tqs.orbit.support.TestData;
import pt.ua.deti.tqs.orbit.support.TestProperties;

class ParcelServiceTest {

    private final MutableClock clock = MutableClock.fixedAt(TestData.T0);
    private final StationRepository stations = mock(StationRepository.class);
    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final CompartmentRepository compartments = mock(CompartmentRepository.class);
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final Station station = TestData.station(1, "ORB-WAW-014", 52.23, 21.01);
    private final Compartment m07 = TestData.compartment(7, station, "M-07", CompartmentSize.M);
    private ParcelService service;

    @BeforeEach
    void setUp() {
        CodeService codes = new CodeService(new SecureRandom(), "s", 6);
        TransitionRecorder tr = new TransitionRecorder(clock);
        service = new ParcelService(stations, parcels, new CompartmentAllocator(compartments, parcels, tr), codes,
                new PinIssuer(codes, parcels), new PricingPolicy(TestProperties.defaults()), tr, notifier, clock,
                TestProperties.defaults());
        when(stations.findByCodeForUpdate("ORB-WAW-014")).thenReturn(Optional.of(station));
        when(compartments.findAllocatable(station, CompartmentSize.M)).thenReturn(List.of(m07));
        when(parcels.findByCompartmentAndStateIn(any(), any())).thenReturn(List.of());
    }

    private RegisterParcelCommand cmd(java.time.Instant start, int hours) {
        return new RegisterParcelCommand("ORB-WAW-014", CompartmentSize.M, null, start, hours, TestData.SENDER,
                TestData.RECIPIENT);
    }

    @Test
    void registersWithLeaseDeadlinePriceAndCodes() {
        RegistrationResult r = service.register(cmd(null, 72));

        assertThat(r.state()).isEqualTo(ParcelState.CREATED);
        assertThat(r.compartmentCode()).isEqualTo("M-07");
        assertThat(r.dropOffDeadline()).isEqualTo(TestData.T0.plus(Duration.ofHours(72)));
        assertThat(r.price()).isEqualByComparingTo("36.00");
        assertThat(r.trackingCode()).matches("ORB-\\d{6}-\\d{4}");
        assertThat(r.dropOffCode()).matches("\\d{6}");
        verify(parcels).save(any(Parcel.class));
        assertThat(notifier.ofType(Notification.Type.REGISTRATION_CONFIRMED)).hasSize(1);
        assertThat(notifier.sent().get(0).body()).doesNotContain(r.dropOffCode());
    }

    @Test
    void leaseStartMustBeWithinTheAllowedWindow() {
        assertThatThrownBy(() -> service.register(cmd(TestData.T0.minus(Duration.ofHours(1)), 48)))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_LEASE_START"));
        assertThatThrownBy(() -> service.register(cmd(TestData.T0.plus(Duration.ofDays(8)), 48)))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_LEASE_START"));
        verify(parcels, never()).save(any());
    }

    @Test
    void unknownStationIsNotFound() {
        when(stations.findByCodeForUpdate("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.register(new RegisterParcelCommand("NOPE", CompartmentSize.M, null, null, 48,
                TestData.SENDER, TestData.RECIPIENT)))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getKind()).isEqualTo(OrbitException.Kind.NOT_FOUND));
    }

    @Test
    void cancelAndCollectFollowTheStateMachine() {
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "h");
        when(parcels.findByTrackingCode("ORB-261024-0001")).thenReturn(Optional.of(p));
        when(parcels.findByDeliveryCode("DLV-ORB-261024-0001")).thenReturn(Optional.of(p));

        service.collect("DLV-ORB-261024-0001", null);
        assertThat(p.getState()).isEqualTo(ParcelState.ON_ITS_WAY);
        assertThat(p.getLocations()).hasSize(1);
        assertThatThrownBy(() -> service.cancel("ORB-261024-0001", Actor.PARTNER, null))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void collectAfterTheLeaseEndedIsRejected() {
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "h");
        when(parcels.findByDeliveryCode("DLV-X")).thenReturn(Optional.of(p));
        clock.advance(Duration.ofHours(48));
        assertThatThrownBy(() -> service.collect("DLV-X", null))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("DROP_OFF_DEADLINE_PASSED"));
    }

    @Test
    void locationUpdatesOnlyWhileOnItsWay() {
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "h");
        when(parcels.findByTrackingCode("ORB-261024-0001")).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.addLocation("orb-261024-0001", "Depot", null, null))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_STATE"));
    }

    @Test
    void unknownTrackingCodeIsNotFound() {
        when(parcels.findByTrackingCode(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.track("ORB-000000-0000"))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("PARCEL_NOT_FOUND"));
    }
}
