package pt.ua.deti.tqs.orbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.domain.Parcel;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.domain.Station;
import pt.ua.deti.tqs.orbit.repository.CompartmentRepository;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;
import pt.ua.deti.tqs.orbit.support.TestData;

/**
 * Unit tests for {@link CompartmentAllocator}.
 *
 * <p>No Spring context is started: the allocator is built with {@code new}, the repositories are Mockito mocks (they
 * are QUERIES here: each test loads them with the answer the database would give) and the clock is fixed. The rule
 * itself, over every parcel state, is in {@code CompartmentAllocationPolicyTest}.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompartmentAllocator — choosing the compartment (repositories mocked)")
class CompartmentAllocatorTest {

    @Mock
    private CompartmentRepository compartments;

    @Mock
    private ParcelRepository parcels;

    private CompartmentAllocator allocator;

    private final Station station = TestData.station(1, "ORB-AVR-001", 40.64, -8.65);
    private final Compartment m03 = TestData.compartment(3, station, "M-03", CompartmentSize.M);
    private final Compartment m07 = TestData.compartment(7, station, "M-07", CompartmentSize.M);
    private final Compartment s01 = TestData.compartment(1, station, "S-01", CompartmentSize.S);

    @BeforeEach
    void setUp() {
        allocator = new CompartmentAllocator(compartments, parcels,
                new TransitionRecorder(MutableClock.fixedAt(TestData.T0)));
    }

    @Test
    @DisplayName("Compartment allocation: the automatic choice is the first free compartment")
    void automaticChoiceIsTheFirstFreeCompartment() {
        when(compartments.findAllocatable(station, CompartmentSize.M)).thenReturn(List.of(m03, m07));

        Compartment allocated = allocator.allocate(station, CompartmentSize.M, null);

        assertThat(allocated).isSameAs(m03);
    }

    @Test
    @DisplayName("Compartment allocation: no free compartment of that size is STATION_FULL")
    void stationFullIsAConflict() {
        when(compartments.findAllocatable(station, CompartmentSize.M)).thenReturn(List.of());

        assertThatThrownBy(() -> allocator.allocate(station, CompartmentSize.M, null))
                .isInstanceOfSatisfying(OrbitException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(OrbitException.Kind.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("STATION_FULL");
                });
    }

    @Test
    @DisplayName("Compartment allocation: a chosen compartment that is reserved is COMPARTMENT_NOT_AVAILABLE")
    void chosenCompartmentMustBeFree() {
        when(compartments.findAllocatable(station, CompartmentSize.M)).thenReturn(List.of(m03));
        when(compartments.findByStationAndCode(station, "M-07")).thenReturn(Optional.of(m07));

        assertThatThrownBy(() -> allocator.allocate(station, CompartmentSize.M, "M-07"))
                .isInstanceOfSatisfying(OrbitException.class,
                        e -> assertThat(e.getCode()).isEqualTo("COMPARTMENT_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("Compartment allocation: a chosen compartment of another size is SIZE_MISMATCH")
    void chosenCompartmentMustBeOfTheParcelSize() {
        when(compartments.findByStationAndCode(station, "S-01")).thenReturn(Optional.of(s01));

        assertThatThrownBy(() -> allocator.allocate(station, CompartmentSize.M, "S-01"))
                .isInstanceOfSatisfying(OrbitException.class, e -> assertThat(e.getCode()).isEqualTo("SIZE_MISMATCH"));
    }

    @Test
    @DisplayName("Compartment allocation: a chosen compartment that does not exist is COMPARTMENT_NOT_FOUND")
    void chosenCompartmentMustExist() {
        when(compartments.findByStationAndCode(station, "X-99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> allocator.allocate(station, CompartmentSize.M, "X-99"))
                .isInstanceOfSatisfying(OrbitException.class,
                        e -> assertThat(e.getCode()).isEqualTo("COMPARTMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("Compartment allocation: reusing an EXPIRED parcel's compartment marks it TO_RETURN")
    void allocatingOverAnExpiredParcelMarksItToReturn() {
        Parcel expired = TestData.parcel(5, "ORB-261001-0005", m03, null);
        expired.apply(ParcelEvent.COLLECT, Actor.COURIER, TestData.T0, null);
        expired.apply(ParcelEvent.DEPOSIT, Actor.COURIER, TestData.T0, null);
        expired.apply(ParcelEvent.PICKUP_DEADLINE_PASSED, Actor.SYSTEM, TestData.T0, null);
        when(compartments.findAllocatable(station, CompartmentSize.M)).thenReturn(List.of(m03));
        when(parcels.findByCompartmentAndStateIn(m03, List.of(ParcelState.EXPIRED))).thenReturn(List.of(expired));

        allocator.allocate(station, CompartmentSize.M, null);

        assertThat(expired.getState()).isEqualTo(ParcelState.TO_RETURN);
        assertThat(expired.getHistory().get(expired.getHistory().size() - 1).getActor()).isEqualTo(Actor.SYSTEM);
    }
}
