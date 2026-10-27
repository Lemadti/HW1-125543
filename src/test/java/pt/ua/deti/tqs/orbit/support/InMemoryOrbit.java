package pt.ua.deti.tqs.orbit.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.transaction.PlatformTransactionManager;
import pt.ua.deti.tqs.orbit.adapter.hardware.FakeLockerHardware;
import pt.ua.deti.tqs.orbit.config.MutableClock;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.domain.Compartment;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.Parcel;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.domain.Station;
import pt.ua.deti.tqs.orbit.port.CompartmentRef;
import pt.ua.deti.tqs.orbit.repository.CompartmentRepository;
import pt.ua.deti.tqs.orbit.repository.IncidentRepository;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;
import pt.ua.deti.tqs.orbit.repository.StationRepository;
import pt.ua.deti.tqs.orbit.service.AttemptGuard;
import pt.ua.deti.tqs.orbit.service.CodeService;
import pt.ua.deti.tqs.orbit.service.CompartmentAllocator;
import pt.ua.deti.tqs.orbit.service.DeadlineService;
import pt.ua.deti.tqs.orbit.service.IncidentReporter;
import pt.ua.deti.tqs.orbit.service.ParcelService;
import pt.ua.deti.tqs.orbit.service.PinIssuer;
import pt.ua.deti.tqs.orbit.service.PricingPolicy;
import pt.ua.deti.tqs.orbit.service.RegisterParcelCommand;
import pt.ua.deti.tqs.orbit.service.TerminalService;
import pt.ua.deti.tqs.orbit.service.TransitionRecorder;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;

/**
 * The real services wired to test doubles, for unit tests without Spring or a database: an in-memory fake of the
 * repositories (Mockito answers over lists), {@link FakeLockerHardware} (doors stay open until closed),
 * {@link RecordingNotifier} and a {@link MutableClock} fixed at {@link TestData#T0}.
 *
 * <p>Create a new instance per test (e.g. as a field of the test class) so that tests share no state. The station
 * {@code ORB-AVR-001} has compartments S-01, M-07 and M-11.</p>
 */
public final class InMemoryOrbit {

    public static final String STATION = "ORB-AVR-001";

    public final OrbitProperties properties = TestProperties.defaults();
    public final MutableClock clock = MutableClock.fixedAt(TestData.T0);
    public final FakeLockerHardware hardware = new FakeLockerHardware(false);
    public final RecordingNotifier notifier = new RecordingNotifier();
    public final CodeService codes = new CodeService(properties);

    public final Station station = TestData.station(1, STATION, 40.6412, -8.6527);
    public final Compartment s01 = TestData.compartment(1, station, "S-01", CompartmentSize.S);
    public final Compartment m07 = TestData.compartment(7, station, "M-07", CompartmentSize.M);
    public final Compartment m11 = TestData.compartment(11, station, "M-11", CompartmentSize.M);
    /** Every parcel known to the fake repository. */
    public final List<Parcel> parcels = new ArrayList<>();

    public final CompartmentAllocator allocator;
    public final ParcelService parcelService;
    public final TerminalService terminal;
    public final DeadlineService deadlines;

    private final List<Compartment> compartments = List.of(s01, m07, m11);

    public InMemoryOrbit() {
        StationRepository stationRepo = mock(StationRepository.class);
        ParcelRepository parcelRepo = mock(ParcelRepository.class);
        CompartmentRepository compartmentRepo = mock(CompartmentRepository.class);
        stubStations(stationRepo);
        stubParcels(parcelRepo);
        stubCompartments(compartmentRepo);

        TransitionRecorder transitions = new TransitionRecorder(clock);
        PinIssuer pins = new PinIssuer(codes, parcelRepo);
        allocator = new CompartmentAllocator(compartmentRepo, parcelRepo, transitions);
        parcelService = new ParcelService(stationRepo, parcelRepo, allocator, codes, pins,
                new PricingPolicy(properties), transitions, notifier, clock, properties);
        terminal = new TerminalService(stationRepo, parcelRepo, hardware, notifier, codes, pins,
                new AttemptGuard(clock, properties), allocator, transitions,
                new IncidentReporter(mock(IncidentRepository.class), notifier, clock), clock, properties);
        deadlines = new DeadlineService(parcelRepo, transitions, notifier, clock,
                mock(PlatformTransactionManager.class));
    }

    // ------------------------------------------------------------------ helpers for the tests

    /**
     * Registers a medium parcel now (UC2): the compartment is allocated at the current clock time.
     *
     * @param compartmentCode compartment to reserve, or null for automatic choice
     * @return the registration, with the drop-off PIN in clear
     */
    public RegistrationResult register(String compartmentCode) {
        return parcelService.register(new RegisterParcelCommand(STATION, CompartmentSize.M, compartmentCode,
                TestData.SENDER, TestData.RECIPIENT));
    }

    /**
     * The parcel with that tracking code.
     *
     * @param trackingCode tracking code
     * @return the parcel
     */
    public Parcel parcel(String trackingCode) {
        return find(p -> p.getTrackingCode().equals(trackingCode)).orElseThrow();
    }

    /**
     * Location of a compartment for the fake hardware.
     *
     * @param compartment the compartment
     * @return its reference
     */
    public static CompartmentRef ref(Compartment compartment) {
        return new CompartmentRef(STATION, compartment.getCode());
    }

    // ------------------------------------------------------------------ in-memory fake of the repositories

    private Optional<Parcel> find(Predicate<Parcel> filter) {
        return parcels.stream().filter(filter).findFirst();
    }

    private boolean exists(Predicate<Parcel> filter) {
        return parcels.stream().anyMatch(filter);
    }

    private static boolean in(Object states, ParcelState state) {
        return ((Collection<?>) states).contains(state);
    }

    private void stubStations(StationRepository repo) {
        when(repo.findByCode(STATION)).thenReturn(Optional.of(station));
        when(repo.findByCodeForUpdate(STATION)).thenReturn(Optional.of(station));
    }

    private void stubParcels(ParcelRepository repo) {
        when(repo.save(any(Parcel.class))).thenAnswer(inv -> {
            parcels.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(repo.findById(any())).thenAnswer(inv -> find(p -> Objects.equals(p.getId(), inv.getArgument(0))));
        when(repo.findByTrackingCode(anyString())).thenAnswer(inv ->
                find(p -> p.getTrackingCode().equals(inv.getArgument(0))));
        when(repo.existsByTrackingCode(anyString())).thenAnswer(inv ->
                exists(p -> p.getTrackingCode().equals(inv.getArgument(0))));
        when(repo.findByDeliveryCode(anyString())).thenAnswer(inv ->
                find(p -> p.getDeliveryCode().equals(inv.getArgument(0))));
        when(repo.existsByDeliveryCode(anyString())).thenAnswer(inv ->
                exists(p -> p.getDeliveryCode().equals(inv.getArgument(0))));
        when(repo.findByStationAndDropOffCodeHashAndStateIn(any(), anyString(), any())).thenAnswer(inv ->
                find(p -> inv.getArgument(1).equals(p.getDropOffCodeHash()) && in(inv.getArgument(2), p.getState())));
        when(repo.existsByStationAndDropOffCodeHashAndStateIn(any(), anyString(), any())).thenAnswer(inv ->
                exists(p -> inv.getArgument(1).equals(p.getDropOffCodeHash()) && in(inv.getArgument(2), p.getState())));
        when(repo.findByStationAndPickupCodeHashAndStateIn(any(), anyString(), any())).thenAnswer(inv ->
                find(p -> inv.getArgument(1).equals(p.getPickupCodeHash()) && in(inv.getArgument(2), p.getState())));
        when(repo.existsByStationAndPickupCodeHashAndStateIn(any(), anyString(), any())).thenAnswer(inv ->
                exists(p -> inv.getArgument(1).equals(p.getPickupCodeHash()) && in(inv.getArgument(2), p.getState())));
        when(repo.findByStationAndTerminalSession(any(), anyString())).thenAnswer(inv ->
                find(p -> p.hasTerminalSession(inv.getArgument(1), clock.instant())));
        when(repo.findByCompartmentAndStateIn(any(), any())).thenAnswer(inv -> parcels.stream()
                .filter(p -> p.getCompartment() == inv.getArgument(0) && in(inv.getArgument(1), p.getState()))
                .toList());
        when(repo.findByStateInAndDropOffDeadlineBefore(any(), any())).thenAnswer(inv -> parcels.stream()
                .filter(p -> inv.<Collection<ParcelState>>getArgument(0).contains(p.getState())
                        && p.getDropOffDeadline().isBefore(inv.getArgument(1)))
                .toList());
        when(repo.findByStateAndPickupDeadlineLessThanEqual(any(), any())).thenAnswer(inv -> parcels.stream()
                .filter(p -> p.getState() == inv.getArgument(0) && p.getPickupDeadline() != null
                        && !p.getPickupDeadline().isAfter(inv.getArgument(1)))
                .toList());
    }

    private void stubCompartments(CompartmentRepository repo) {
        when(repo.findByStationAndCode(any(), anyString())).thenAnswer(inv -> compartments.stream()
                .filter(c -> c.getCode().equals(inv.getArgument(1))).findFirst());
        // same condition as the JPQL query: in service, same size, no parcel holding a reservation
        when(repo.findAllocatable(any(), any())).thenAnswer(inv -> compartments.stream()
                .filter(c -> c.getSize() == inv.getArgument(1) && c.isInService())
                .filter(c -> !exists(p -> p.getCompartment() == c && ParcelState.RESERVING.contains(p.getState())))
                .toList());
    }
}
