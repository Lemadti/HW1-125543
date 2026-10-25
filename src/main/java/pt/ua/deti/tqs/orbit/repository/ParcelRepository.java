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

/** Persistence of parcels and the lookups used by the terminal, the deadline job and the operator. */
public interface ParcelRepository extends JpaRepository<Parcel, Long> {

    /**
     * Finds a parcel by tracking code (with station and compartment).
     *
     * @param trackingCode tracking code
     * @return the parcel, if it exists
     */
    @EntityGraph(attributePaths = {"station", "compartment"})
    Optional<Parcel> findByTrackingCode(String trackingCode);

    /**
     * Finds a parcel by the QR delivery code.
     *
     * @param deliveryCode delivery code
     * @return the parcel, if it exists
     */
    Optional<Parcel> findByDeliveryCode(String deliveryCode);

    /**
     * Whether a tracking code is taken.
     *
     * @param trackingCode tracking code
     * @return true if used
     */
    boolean existsByTrackingCode(String trackingCode);

    /**
     * Whether a delivery code is taken.
     *
     * @param deliveryCode delivery code
     * @return true if used
     */
    boolean existsByDeliveryCode(String deliveryCode);

    /**
     * Finds the parcel whose drop-off PIN matches at a station.
     *
     * @param station the station
     * @param hash    HMAC of the PIN typed
     * @param states  states in which the PIN is valid
     * @return the parcel, if any
     */
    Optional<Parcel> findByStationAndDropOffCodeHashAndStateIn(Station station, String hash,
                                                              Collection<ParcelState> states);

    /**
     * Finds the parcel whose pickup PIN matches at a station.
     *
     * @param station the station
     * @param hash    HMAC of the PIN typed
     * @param states  states in which the PIN is valid
     * @return the parcel, if any
     */
    Optional<Parcel> findByStationAndPickupCodeHashAndStateIn(Station station, String hash,
                                                             Collection<ParcelState> states);

    /**
     * Whether a drop-off PIN hash is already in use at a station.
     *
     * @param station the station
     * @param hash    PIN hash
     * @param states  states to consider
     * @return true if in use
     */
    boolean existsByStationAndDropOffCodeHashAndStateIn(Station station, String hash, Collection<ParcelState> states);

    /**
     * Whether a pickup PIN hash is already in use at a station.
     *
     * @param station the station
     * @param hash    PIN hash
     * @param states  states to consider
     * @return true if in use
     */
    boolean existsByStationAndPickupCodeHashAndStateIn(Station station, String hash, Collection<ParcelState> states);

    /**
     * Finds the parcel of an ongoing terminal interaction.
     *
     * @param station         the station
     * @param terminalSession session token
     * @return the parcel, if any
     */
    Optional<Parcel> findByStationAndTerminalSession(Station station, String terminalSession);

    /**
     * Parcels of a station in the given states, newest first.
     *
     * @param station the station
     * @param states  states to include
     * @return the parcels
     */
    @EntityGraph(attributePaths = {"compartment"})
    List<Parcel> findByStationAndStateInOrderByCreatedAtDesc(Station station, Collection<ParcelState> states);

    /**
     * All parcels of a station, newest first.
     *
     * @param station the station
     * @return the parcels
     */
    @EntityGraph(attributePaths = {"compartment"})
    List<Parcel> findByStationOrderByCreatedAtDesc(Station station);

    /**
     * Parcels in a compartment with the given states.
     *
     * @param compartment the compartment
     * @param states      states to include
     * @return the parcels
     */
    List<Parcel> findByCompartmentAndStateIn(Compartment compartment, Collection<ParcelState> states);

    /**
     * Parcels in a state whose drop-off deadline has passed.
     *
     * @param state    the state (CREATED)
     * @param deadline the current time
     * @return the parcels
     */
    List<Parcel> findByStateAndDropOffDeadlineLessThanEqual(ParcelState state, Instant deadline);

    /**
     * Parcels in a state whose pickup deadline has passed.
     *
     * @param state    the state (READY_FOR_PICKUP)
     * @param deadline the current time
     * @return the parcels
     */
    List<Parcel> findByStateAndPickupDeadlineLessThanEqual(ParcelState state, Instant deadline);

    /**
     * The 20 most recently registered parcels.
     *
     * @return the parcels
     */
    @EntityGraph(attributePaths = {"station", "compartment"})
    List<Parcel> findTop20ByOrderByCreatedAtDesc();
}
