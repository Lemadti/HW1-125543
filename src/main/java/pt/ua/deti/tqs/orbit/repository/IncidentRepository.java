package pt.ua.deti.tqs.orbit.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.ua.deti.tqs.orbit.domain.Incident;
import pt.ua.deti.tqs.orbit.domain.Station;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    @EntityGraph(attributePaths = {"compartment"})
    List<Incident> findTop50ByCompartmentStationOrderByReportedAtDesc(Station station);
}
