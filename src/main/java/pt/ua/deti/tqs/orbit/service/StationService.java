package pt.ua.deti.tqs.orbit.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.domain.Compartment;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.GeoPoint;
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.domain.Parcel;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.domain.Station;
import pt.ua.deti.tqs.orbit.port.GeocoderPort;
import pt.ua.deti.tqs.orbit.repository.CompartmentRepository;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;
import pt.ua.deti.tqs.orbit.repository.StationRepository;
import pt.ua.deti.tqs.orbit.service.view.CompartmentView;
import pt.ua.deti.tqs.orbit.service.view.NearbySearchResult;
import pt.ua.deti.tqs.orbit.service.view.SizeCounts;
import pt.ua.deti.tqs.orbit.service.view.StationDetail;
import pt.ua.deti.tqs.orbit.service.view.StationSummary;

/** UC1 (find a locker) and station availability. Distance and ranking are computed here, not by the provider. */
@Service
@Transactional(readOnly = true)
public class StationService {

    private final StationRepository stations;
    private final CompartmentRepository compartments;
    private final ParcelRepository parcels;
    private final GeocoderPort geocoder;
    private final OrbitProperties.Search search;

    /**
     * Creates the service.
     *
     * @param stations     station repository
     * @param compartments compartment repository
     * @param parcels      parcel repository
     * @param geocoder     geocoding port
     * @param properties   search radius and result limit
     */
    public StationService(StationRepository stations, CompartmentRepository compartments, ParcelRepository parcels,
                            GeocoderPort geocoder, OrbitProperties properties) {
        this.stations = stations;
        this.compartments = compartments;
        this.parcels = parcels;
        this.geocoder = geocoder;
        this.search = properties.search();
    }

    /**
     * All stations alphabetically, with free compartments; the fallback when geocoding is unavailable.
     *
     * @param size if not null, only stations with a free compartment of this size
     * @return the stations (without distance)
     */
    public List<StationSummary> listAll(CompartmentSize size) {
        Map<Long, SizeCounts> free = freeCounts();
        return stations.findAllByOrderByNameAsc().stream()
                .map(s -> summary(s, free, null))
                .filter(s -> size == null || s.free().of(size) > 0)
                .toList();
    }

    /**
     * Geocodes the address and returns the stations within the search radius, nearest first.
     *
     * @param address free-text address
     * @param size    optional size filter
     * @return the ranked stations
     * @throws OrbitException ADDRESS_REQUIRED or ADDRESS_NOT_FOUND
     * @throws pt.ua.deti.tqs.orbit.port.GeocodingUnavailableException if the provider cannot answer
     */
    public NearbySearchResult nearbyAddress(String address, CompartmentSize size) {
        if (address == null || address.isBlank()) {
            throw OrbitException.unprocessable("ADDRESS_REQUIRED", "Please enter an address");
        }
        GeoPoint origin = geocoder.geocode(address.trim())
                .orElseThrow(() -> OrbitException.notFound("ADDRESS_NOT_FOUND",
                        "We could not find \"" + address.trim() + "\". Try adding the street number and city."));
        return nearby(address.trim(), origin, size);
    }

    /**
     * Stations within the search radius of a point, nearest first.
     *
     * @param origin the point
     * @param size   optional size filter
     * @return the ranked stations
     */
    public NearbySearchResult nearbyPoint(GeoPoint origin, CompartmentSize size) {
        return nearby(null, origin, size);
    }

    /**
     * Ranks the stations around an origin.
     *
     * @param query  the address searched, or null
     * @param origin the origin point
     * @param size   optional size filter
     * @return the result
     */
    NearbySearchResult nearby(String query, GeoPoint origin, CompartmentSize size) {
        Map<Long, SizeCounts> free = freeCounts();
        List<StationSummary> ranked = rank(origin, stations.findAll(), free, size, search.radiusKm(),
                search.maxResults());
        return new NearbySearchResult(query, origin.latitude(), origin.longitude(), search.radiusKm(), ranked);
    }

    /**
     * Ranking rule: within the radius, optionally with a free compartment of the size, nearest first, ties
     * broken by name.
     *
     * @param origin     origin point
     * @param all        candidate stations
     * @param free       free compartments per station id
     * @param size       optional size filter
     * @param radiusKm   maximum distance
     * @param maxResults maximum number of stations
     * @return the ranked summaries
     */
    List<StationSummary> rank(GeoPoint origin, List<Station> all, Map<Long, SizeCounts> free, CompartmentSize size,
                              double radiusKm, int maxResults) {
        return all.stream()
                .map(s -> summary(s, free, DistanceCalculator.distanceKm(origin, s.location())))
                .filter(s -> s.distanceKm() <= radiusKm)
                .filter(s -> size == null || s.free().of(size) > 0)
                .sorted(Comparator.comparingDouble(StationSummary::distanceKm).thenComparing(StationSummary::name))
                .limit(maxResults)
                .toList();
    }

    /**
     * A station with every compartment and its availability (front view).
     *
     * @param code station code
     * @return the station detail
     * @throws OrbitException STATION_NOT_FOUND
     */
    public StationDetail detail(String code) {
        Station station = stations.findByCode(code)
                .orElseThrow(() -> OrbitException.notFound("STATION_NOT_FOUND", "Station " + code + " not found"));
        Map<Long, Parcel> reservations = parcels
                .findByStationAndStateInOrderByCreatedAtDesc(station, ParcelState.RESERVING).stream()
                .collect(Collectors.toMap(p -> p.getCompartment().getId(), Function.identity(), (a, b) -> a));
        List<CompartmentView> views = compartments.findByStationOrderByPositionAsc(station).stream()
                .map(c -> compartmentView(c, reservations.get(c.getId())))
                .toList();
        return new StationDetail(summary(station, freeCounts(), null), views);
    }

    private static CompartmentView compartmentView(Compartment c, Parcel reservation) {
        String availability;
        Instant until = null;
        if (!c.isInService()) {
            availability = "OUT_OF_SERVICE";
        } else if (reservation != null) {
            availability = "RESERVED";
            until = reservation.getState() == ParcelState.READY_FOR_PICKUP
                    ? reservation.getPickupDeadline() : reservation.getDropOffDeadline();
        } else {
            availability = "AVAILABLE";
        }
        CompartmentSize size = c.getSize();
        return new CompartmentView(c.getCode(), c.getPosition(), (c.getPosition() - 1) / 4, (c.getPosition() - 1) % 4,
                size, size.heightCm(), size.widthCm(), size.depthCm(), availability, until, c.getStatusReason());
    }

    private StationSummary summary(Station s, Map<Long, SizeCounts> free, Double distanceKm) {
        Double rounded = distanceKm == null ? null : Math.round(distanceKm * 100.0) / 100.0;
        return new StationSummary(s.getCode(), s.getName(), s.getAddress(), s.getPostalCode(), s.getCity(),
                s.getLatitude(), s.getLongitude(), s.getAccessNotes(), compartments.countByStation(s),
                free.getOrDefault(s.getId(), SizeCounts.NONE), rounded,
                distanceKm == null ? null : DistanceCalculator.walkingMinutes(distanceKm));
    }

    private Map<Long, SizeCounts> freeCounts() {
        Map<Long, SizeCounts> result = new HashMap<>();
        for (Object[] row : compartments.countAllocatableByStationAndSize()) {
            Long stationId = (Long) row[0];
            CompartmentSize size = (CompartmentSize) row[1];
            long count = ((Number) row[2]).longValue();
            result.merge(stationId, SizeCounts.NONE.with(size, count), (a, b) -> a.with(size, count));
        }
        return result;
    }
}
