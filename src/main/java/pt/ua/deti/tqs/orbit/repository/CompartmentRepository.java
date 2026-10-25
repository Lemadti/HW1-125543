package pt.ua.deti.tqs.orbit.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.ua.deti.tqs.orbit.domain.Compartment;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.Station;

public interface CompartmentRepository extends JpaRepository<Compartment, Long> {

    List<Compartment> findByStationOrderByPositionAsc(Station station);

    Optional<Compartment> findByStationAndCode(Station station, String code);

    /**
     * Compartments that can receive a new parcel: in service, of the requested size, and with no parcel holding a
     * reservation (CREATED, ON_ITS_WAY, READY_FOR_PICKUP). A compartment with an EXPIRED parcel is allocatable.
     */
    @Query("""
            select c from Compartment c
            where c.station = :station and c.size = :size
              and c.status = pt.ua.deti.tqs.orbit.domain.CompartmentStatus.IN_SERVICE
              and not exists (select p from Parcel p where p.compartment = c
                              and p.state in (pt.ua.deti.tqs.orbit.domain.ParcelState.CREATED,
                                              pt.ua.deti.tqs.orbit.domain.ParcelState.ON_ITS_WAY,
                                              pt.ua.deti.tqs.orbit.domain.ParcelState.READY_FOR_PICKUP))
            order by c.position asc
            """)
    List<Compartment> findAllocatable(@Param("station") Station station, @Param("size") CompartmentSize size);

    /** Allocatable compartments per station and size: rows of [stationId, size, count]. */
    @Query("""
            select c.station.id, c.size, count(c) from Compartment c
            where c.status = pt.ua.deti.tqs.orbit.domain.CompartmentStatus.IN_SERVICE
              and not exists (select p from Parcel p where p.compartment = c
                              and p.state in (pt.ua.deti.tqs.orbit.domain.ParcelState.CREATED,
                                              pt.ua.deti.tqs.orbit.domain.ParcelState.ON_ITS_WAY,
                                              pt.ua.deti.tqs.orbit.domain.ParcelState.READY_FOR_PICKUP))
            group by c.station.id, c.size
            """)
    List<Object[]> countAllocatableByStationAndSize();

    @Query("select count(c) from Compartment c where c.station = :station")
    long countByStation(@Param("station") Station station);
}
