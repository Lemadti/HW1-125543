package pt.ua.deti.tqs.orbit.web.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.domain.GeoPoint;
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.service.StationService;
import pt.ua.deti.tqs.orbit.service.view.NearbySearchResult;
import pt.ua.deti.tqs.orbit.service.view.StationDetail;
import pt.ua.deti.tqs.orbit.service.view.StationSummary;

/** REST endpoints for finding stations and their availability (UC1). */
@RestController
@RequestMapping("/api/v1/stations")
@Tag(name = "Stations", description = "UC1 - find a locker station")
public class StationApiController {

    private final StationService stations;

    /**
     * Creates the controller.
     *
     * @param stations station use cases
     */
    public StationApiController(StationService stations) {
        this.stations = stations;
    }

    /**
     * {@code GET /api/v1/stations}: every station with its free compartments.
     *
     * @param size optional size filter
     * @return the stations
     */
    @GetMapping
    @Operation(summary = "All stations with free compartments per size (optionally only those with a free compartment of `size`)")
    public List<StationSummary> all(@RequestParam(required = false) CompartmentSize size) {
        return stations.listAll(size);
    }

    /**
     * {@code GET /api/v1/stations/nearby}: stations near an address or a coordinate (UC1).
     *
     * @param address free-text address (used when lat/lon are absent)
     * @param lat     optional latitude
     * @param lon     optional longitude
     * @param size    optional size filter
     * @return stations within the radius, nearest first
     */
    @GetMapping("/nearby")
    @Operation(summary = "Stations near an address (geocoded) or a coordinate, nearest first",
            description = "Pass `address`, or `lat` and `lon`. 404 ADDRESS_NOT_FOUND if the address is unknown; "
                    + "503 GEOCODER_UNAVAILABLE if the provider is slow, down or over its usage limit "
                    + "(clients should fall back to GET /api/v1/stations).")
    public NearbySearchResult nearby(@RequestParam(required = false) String address,
                                     @RequestParam(required = false) Double lat,
                                     @RequestParam(required = false) Double lon,
                                     @RequestParam(required = false) CompartmentSize size) {
        if (lat != null && lon != null) {
            try {
                return stations.nearbyPoint(new GeoPoint(lat, lon), size);
            } catch (IllegalArgumentException e) {
                throw OrbitException.unprocessable("INVALID_COORDINATES", e.getMessage());
            }
        }
        return stations.nearbyAddress(address, size);
    }

    /**
     * {@code GET /api/v1/stations/{code}}: station detail with the compartment front view.
     *
     * @param code station code
     * @return the station detail
     */
    @GetMapping("/{code}")
    @Operation(summary = "Station detail with every compartment (front view) and its availability")
    public StationDetail detail(@PathVariable String code) {
        return stations.detail(code);
    }
}
