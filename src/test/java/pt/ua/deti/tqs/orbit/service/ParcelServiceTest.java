package pt.ua.deti.tqs.orbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

/**
 * Unit tests for {@link ParcelService}.
 *
 * <p>No Spring context is started: the service is built with {@code new}, the repositories are Mockito mocks and the
 * clock is fixed. The real {@link CompartmentAllocator}, {@link PinIssuer} and {@link PricingPolicy} are used: they
 * are plain, deterministic objects, so there is nothing to gain by mocking them. Notifications go to a recording
 * fake, so the oracle is what would have been sent.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ParcelService — registration, collection, tracking (repositories mocked)")
class ParcelServiceTest {

    private final MutableClock clock = MutableClock.fixedAt(TestData.T0);
    @Mock
    private StationRepository stations;
    @Mock
    private ParcelRepository parcels;
    @Mock
    private CompartmentRepository compartments;

    private final RecordingNotifier notifier = new RecordingNotifier();
    private final Station station = TestData.station(1, "ORB-AVR-001", 40.64, -8.65);
    private final Compartment m07 = TestData.compartment(7, station, "M-07", CompartmentSize.M);
    private ParcelService service;

    @BeforeEach
    void setUp() {
        CodeService codes = new CodeService(new SecureRandom(), "s", 6);
        TransitionRecorder tr = new TransitionRecorder(clock);
        service = new ParcelService(stations, parcels, new CompartmentAllocator(compartments, parcels, tr), codes,
                new PinIssuer(codes, parcels), new PricingPolicy(TestProperties.defaults()), tr, notifier, clock,
                TestProperties.defaults());
    }

    private RegisterParcelCommand cmd() {
        return new RegisterParcelCommand("ORB-AVR-001", CompartmentSize.M, null, TestData.SENDER, TestData.RECIPIENT);
    }

    @Test
    @DisplayName("US2: registration reserves a compartment and returns deadline, price and codes")
    void registersWithDropOffDeadlinePriceAndCodes() {
        when(stations.findByCodeForUpdate("ORB-AVR-001")).thenReturn(Optional.of(station));
        when(compartments.findAllocatable(station, CompartmentSize.M)).thenReturn(List.of(m07));
        when(parcels.findByCompartmentAndStateIn(any(), any())).thenReturn(List.of());

        RegistrationResult r = service.register(cmd());

        assertThat(r.state()).isEqualTo(ParcelState.CREATED);
        assertThat(r.compartmentCode()).isEqualTo("M-07");
        assertThat(r.dropOffDeadline()).isEqualTo(TestData.DROP_OFF_DEADLINE);
        assertThat(r.price()).isEqualByComparingTo("3.00");
        assertThat(r.trackingCode()).matches("ORB-\\d{6}-\\d{4}");
        assertThat(r.dropOffCode()).matches("\\d{6}");
        verify(parcels).save(any(Parcel.class));
        assertThat(notifier.ofType(Notification.Type.REGISTRATION_CONFIRMED)).hasSize(1);
        assertThat(notifier.sent().get(0).body()).doesNotContain(r.dropOffCode());
    }

    @Test
    @DisplayName("US2: an unknown station is refused and nothing is saved")
    void unknownStationIsNotFound() {
        when(stations.findByCodeForUpdate("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.register(new RegisterParcelCommand("NOPE", CompartmentSize.M, null,
                TestData.SENDER, TestData.RECIPIENT)))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getKind()).isEqualTo(OrbitException.Kind.NOT_FOUND));

        verify(parcels, never()).save(any());
        assertThat(notifier.sent()).isEmpty();
    }

    @Test
    @DisplayName("State machine: after collection the parcel can no longer be cancelled")
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
    @DisplayName("Drop-off deadline: the courier cannot collect after the deadline")
    void collectAfterTheDropOffDeadlineIsRejected() {
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "h");
        when(parcels.findByDeliveryCode("DLV-X")).thenReturn(Optional.of(p));
        clock.setFixed(TestData.DROP_OFF_DEADLINE.plusSeconds(1));
        assertThatThrownBy(() -> service.collect("DLV-X", null))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("DROP_OFF_DEADLINE_PASSED"));
    }

    @Test
    @DisplayName("US5: locations are only reported while the parcel is on its way")
    void locationUpdatesOnlyWhileOnItsWay() {
        Parcel p = TestData.parcel(1, "ORB-261024-0001", m07, "h");
        when(parcels.findByTrackingCode("ORB-261024-0001")).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.addLocation("orb-261024-0001", "Depot", null, null))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_STATE"));
    }

    @Test
    @DisplayName("US5: an unknown tracking code is PARCEL_NOT_FOUND")
    void unknownTrackingCodeIsNotFound() {
        when(parcels.findByTrackingCode(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.track("ORB-000000-0000"))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("PARCEL_NOT_FOUND"));
    }
}
