package pt.ua.deti.tqs.orbit.web.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.web.api.dto.PublicConfig;

/** REST endpoint with the public business parameters used by the pages. */
@RestController
@Tag(name = "Configuration")
public class ConfigApiController {

    private final OrbitProperties properties;

    /**
     * Creates the controller.
     *
     * @param properties business rules
     */
    public ConfigApiController(OrbitProperties properties) {
        this.properties = properties;
    }

    /**
     * {@code GET /api/v1/config}: public business parameters for the pages.
     *
     * @return lease options, price, currency, pickup window, radius options and assistance phone
     */
    @GetMapping("/api/v1/config")
    @Operation(summary = "Public business parameters (lease options, prices, search radius options)")
    public PublicConfig config() {
        var rules = properties.rules();
        return new PublicConfig(rules.leaseHoursOptions(), rules.defaultLeaseHours(), rules.pricePerDay(),
                rules.currency(), rules.pickupWindow().toDays(), properties.search().radiusOptionsKm(),
                properties.search().maxRadiusKm(),
                properties.assistancePhone(), properties.demo().enabled());
    }
}
