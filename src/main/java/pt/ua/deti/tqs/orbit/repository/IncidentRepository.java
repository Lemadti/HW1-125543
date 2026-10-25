package pt.ua.deti.tqs.orbit.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.ua.deti.tqs.orbit.domain.Incident;
import pt.ua.deti.tqs.orbit.domain.Station;

/** Persistence of malfunction reports. */
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    /**
     * The last 50 incidents of a station.
     *
     * @param station the station
     * @return the incidents, newest first
     */
    @EntityGraph(attributePaths = {"compartment"})
    List<Incident> findTop50ByCompartmentStationOrderByReportedAtDesc(Station station);
}
