package pt.ua.deti.tqs.orbit.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.ua.deti.tqs.orbit.domain.Station;

public interface StationRepository extends JpaRepository<Station, Long> {

    Optional<Station> findByCode(String code);

    List<Station> findAllByOrderByNameAsc();

    /**
     * Locks the station row (SELECT ... FOR UPDATE) so that compartment allocations in the same station are
     * serialised: two partners can never get the same last compartment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Station s where s.code = :code")
    Optional<Station> findByCodeForUpdate(@Param("code") String code);
}
