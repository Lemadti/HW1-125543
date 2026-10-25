package pt.ua.deti.tqs.orbit.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.Compartment;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.Parcel;
import pt.ua.deti.tqs.orbit.domain.ParcelEvent;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.domain.Station;
import pt.ua.deti.tqs.orbit.repository.CompartmentRepository;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;
import pt.ua.deti.tqs.orbit.repository.StationRepository;
import pt.ua.deti.tqs.orbit.support.AbstractIntegrationTest;
import pt.ua.deti.tqs.orbit.support.PostgresTestConfig;
import pt.ua.deti.tqs.orbit.support.TestData;

/**
 * JPA slice against real PostgreSQL: Flyway schema + seed, the allocation query, and the partial unique index that
 * guarantees one active reservation per compartment.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestConfig.class)
@ActiveProfiles("test")
class RepositoryIT {

    @Autowired
    StationRepository stations;
    @Autowired
    CompartmentRepository compartments;
    @Autowired
    ParcelRepository parcels;
    @Autowired
    JdbcTemplate jdbc;

    private Station centrum;

    @BeforeEach
    void setUp() {
        AbstractIntegrationTest.resetDatabase(jdbc);
        centrum = stations.findByCode("ORB-AVR-001").orElseThrow();
    }

    private Parcel newParcel(String code, Compartment c) {
        return Parcel.register(code, "DLV-" + code, c, TestData.SENDER, TestData.RECIPIENT, TestData.T0, 48,
                new BigDecimal("24.00"), "h-" + code, TestData.T0);
    }

    @Test
    void seedDataHasFiveAveiroZoneStationsWithMixedSizes() {
        assertThat(stations.findAll()).hasSize(5).allSatisfy(s -> {
            assertThat(s.getCity()).isIn("Aveiro", "Ílhavo");
            assertThat(s.getLatitude()).isBetween(40.55, 40.70);
            assertThat(s.getLongitude()).isBetween(-8.75, -8.60);
        });
        List<Compartment> bank = compartments.findByStationOrderByPositionAsc(centrum);
        assertThat(bank).hasSize(16);
        assertThat(bank).filteredOn(c -> c.getSize() == CompartmentSize.M).extracting(Compartment::getCode)
                .containsExactly("M-03", "M-07", "M-11", "M-15");
    }

    @Test
    void allocatableExcludesOutOfServiceAndReservedCompartments() {
        assertThat(compartments.findAllocatable(centrum, CompartmentSize.S)).extracting(Compartment::getCode)
                .doesNotContain("S-06").hasSize(7);
        Compartment m03 = compartments.findByStationAndCode(centrum, "M-03").orElseThrow();
        parcels.saveAndFlush(newParcel("ORB-261024-0001", m03));

        assertThat(compartments.findAllocatable(centrum, CompartmentSize.M)).extracting(Compartment::getCode)
                .containsExactly("M-07", "M-11", "M-15");
        assertThat(compartments.countAllocatableByStationAndSize())
                .anySatisfy(row -> assertThat(row).containsExactly(centrum.getId(), CompartmentSize.M, 3L));
    }

    @Test
    void expiredParcelDoesNotBlockTheCompartment() {
        Compartment m03 = compartments.findByStationAndCode(centrum, "M-03").orElseThrow();
        Parcel p = newParcel("ORB-261024-0002", m03);
        p.apply(ParcelEvent.COLLECT, Actor.COURIER, TestData.T0, null);
        p.apply(ParcelEvent.DEPOSIT, Actor.COURIER, TestData.T0, null);
        p.apply(ParcelEvent.PICKUP_DEADLINE_PASSED, Actor.SYSTEM, TestData.T0, null);
        parcels.saveAndFlush(p);

        assertThat(compartments.findAllocatable(centrum, CompartmentSize.M)).extracting(Compartment::getCode).contains("M-03");
        parcels.saveAndFlush(newParcel("ORB-261024-0003", m03)); // allowed to coexist with the EXPIRED one
        assertThat(parcels.findByCompartmentAndStateIn(m03, List.of(ParcelState.EXPIRED, ParcelState.CREATED))).hasSize(2);
    }

    @Test
    void databaseRejectsTwoActiveReservationsOnTheSameCompartment() {
        Compartment m07 = compartments.findByStationAndCode(centrum, "M-07").orElseThrow();
        parcels.saveAndFlush(newParcel("ORB-261024-0004", m07));
        assertThatThrownBy(() -> parcels.saveAndFlush(newParcel("ORB-261024-0005", m07)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void historyIsPersistedInOrder() {
        Compartment m07 = compartments.findByStationAndCode(centrum, "M-07").orElseThrow();
        Parcel p = newParcel("ORB-261024-0006", m07);
        p.apply(ParcelEvent.COLLECT, Actor.COURIER, TestData.T0.plusSeconds(10), null);
        parcels.saveAndFlush(p);

        Parcel reloaded = parcels.findByTrackingCode("ORB-261024-0006").orElseThrow();
        assertThat(reloaded.getHistory()).extracting(t -> t.getToState())
                .containsExactly(ParcelState.CREATED, ParcelState.ON_ITS_WAY);
    }
}
