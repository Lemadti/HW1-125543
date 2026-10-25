package pt.ua.deti.tqs.orbit.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.ua.deti.tqs.orbit.domain.Station;

/** Persistence of locker stations (seed data). */
public interface StationRepository extends JpaRepository<Station, Long> {

    /**
     * Finds a station by its public code.
     *
     * @param code station code
     * @return the station, if it exists
     */
    Optional<Station> findByCode(String code);

    /**
     * All stations, alphabetically.
     *
     * @return the stations
     */
    List<Station> findAllByOrderByNameAsc();

    /**
     * Locks the station row (SELECT ... FOR UPDATE) so that compartment allocations in the same station are
     * serialised: two partners can never get the same last compartment.
     *
     * @param code station code
     * @return the locked station, if it exists
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Station s where s.code = :code")
    Optional<Station> findByCodeForUpdate(@Param("code") String code);
}
