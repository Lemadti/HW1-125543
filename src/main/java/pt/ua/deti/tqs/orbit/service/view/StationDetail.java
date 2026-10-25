package pt.ua.deti.tqs.orbit.service.view;

import java.util.List;

public record StationDetail(StationSummary station, List<CompartmentView> compartments) {
}
