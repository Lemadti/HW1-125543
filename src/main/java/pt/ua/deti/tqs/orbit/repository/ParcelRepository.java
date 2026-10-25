package pt.ua.deti.tqs.orbit.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.ua.deti.tqs.orbit.domain.Compartment;
import pt.ua.deti.tqs.orbit.domain.Parcel;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.domain.Station;

public interface ParcelRepository extends JpaRepository<Parcel, Long> {

    @EntityGraph(attributePaths = {"station", "compartment"})
    Optional<Parcel> findByTrackingCode(String trackingCode);

    Optional<Parcel> findByDeliveryCode(String deliveryCode);

    boolean existsByTrackingCode(String trackingCode);

    boolean existsByDeliveryCode(String deliveryCode);

    Optional<Parcel> findByStationAndDropOffCodeHashAndStateIn(Station station, String hash,
                                                              Collection<ParcelState> states);

    Optional<Parcel> findByStationAndPickupCodeHashAndStateIn(Station station, String hash,
                                                             Collection<ParcelState> states);

    boolean existsByStationAndDropOffCodeHashAndStateIn(Station station, String hash, Collection<ParcelState> states);

    boolean existsByStationAndPickupCodeHashAndStateIn(Station station, String hash, Collection<ParcelState> states);

    Optional<Parcel> findByStationAndTerminalSession(Station station, String terminalSession);

    @EntityGraph(attributePaths = {"compartment"})
    List<Parcel> findByStationAndStateInOrderByCreatedAtDesc(Station station, Collection<ParcelState> states);

    @EntityGraph(attributePaths = {"compartment"})
    List<Parcel> findByStationOrderByCreatedAtDesc(Station station);

    List<Parcel> findByCompartmentAndStateIn(Compartment compartment, Collection<ParcelState> states);

    List<Parcel> findByStateAndDropOffDeadlineLessThanEqual(ParcelState state, Instant deadline);

    List<Parcel> findByStateAndPickupDeadlineLessThanEqual(ParcelState state, Instant deadline);

    @EntityGraph(attributePaths = {"station", "compartment"})
    List<Parcel> findTop20ByOrderByCreatedAtDesc();
}
