package pt.ua.deti.tqs.orbit.service;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
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

/**
 * Compartment allocation rules:
 * <ul>
 *   <li>only compartments of the same size as the parcel, in service;</li>
 *   <li>one reservation per compartment (CREATED / ON_ITS_WAY / READY_FOR_PICKUP);</li>
 *   <li>a compartment holding an EXPIRED parcel can be allocated: that parcel becomes TO_RETURN and is removed by the
 *       courier on the next drop-off;</li>
 *   <li>callers must hold the station lock ({@code StationRepository.findByCodeForUpdate}).</li>
 * </ul>
 */
@Component
public class CompartmentAllocator {

    private static final Logger log = LoggerFactory.getLogger(CompartmentAllocator.class);

    private final CompartmentRepository compartments;
    private final ParcelRepository parcels;
    private final TransitionRecorder transitions;

    public CompartmentAllocator(CompartmentRepository compartments, ParcelRepository parcels,
                                TransitionRecorder transitions) {
        this.compartments = compartments;
        this.parcels = parcels;
        this.transitions = transitions;
    }

    /** First allocatable compartment of that size, if any (does not reserve anything). */
    public Optional<Compartment> findFree(Station station, CompartmentSize size) {
        List<Compartment> free = compartments.findAllocatable(station, size);
        return free.isEmpty() ? Optional.empty() : Optional.of(free.get(0));
    }

    /**
     * Chooses the compartment for a new reservation.
     *
     * @param preferredCode compartment chosen by the user, or null for automatic choice
     */
    public Compartment allocate(Station station, CompartmentSize size, String preferredCode) {
        List<Compartment> free = compartments.findAllocatable(station, size);
        Compartment chosen;
        if (preferredCode != null && !preferredCode.isBlank()) {
            Compartment requested = compartments.findByStationAndCode(station, preferredCode)
                    .orElseThrow(() -> OrbitException.notFound("COMPARTMENT_NOT_FOUND",
                            "Compartment " + preferredCode + " does not exist at station " + station.getCode()));
            if (requested.getSize() != size) {
                throw OrbitException.unprocessable("SIZE_MISMATCH",
                        "Compartment " + preferredCode + " is size " + requested.getSize() + ", parcel is " + size);
            }
            chosen = free.stream().filter(c -> c.getId().equals(requested.getId())).findFirst()
                    .orElseThrow(() -> OrbitException.conflict("COMPARTMENT_NOT_AVAILABLE",
                            "Compartment " + preferredCode + " is no longer available"));
        } else {
            if (free.isEmpty()) {
                throw OrbitException.conflict("STATION_FULL",
                        "No " + size.label().toLowerCase() + " compartment available at station " + station.getCode());
            }
            chosen = free.get(0);
        }
        markExpiredParcelsToReturn(chosen);
        log.info("ALLOCATE station={} size={} -> {}", station.getCode(), size, chosen.getCode());
        return chosen;
    }

    private void markExpiredParcelsToReturn(Compartment compartment) {
        for (Parcel expired : parcels.findByCompartmentAndStateIn(compartment, List.of(ParcelState.EXPIRED))) {
            transitions.apply(expired, ParcelEvent.MARK_TO_RETURN, Actor.SYSTEM,
                    "Compartment " + compartment.getCode() + " re-allocated; to be returned on next drop-off");
        }
    }
}
