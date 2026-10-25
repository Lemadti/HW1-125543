package pt.ua.deti.tqs.orbit.service.view;

import java.util.List;

/**
 * A station and its compartments.
 *
 * @param station      the station summary
 * @param compartments every compartment, in bank order
 */
public record StationDetail(StationSummary station, List<CompartmentView> compartments) {
}
