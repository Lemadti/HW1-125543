package pt.ua.deti.tqs.orbit.web.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.web.api.dto.PublicConfig;

@RestController
@Tag(name = "Configuration")
public class ConfigApiController {

    private final OrbitProperties properties;

    public ConfigApiController(OrbitProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/v1/config")
    @Operation(summary = "Public business parameters (lease options, prices, search radius)")
    public PublicConfig config() {
        var rules = properties.rules();
        return new PublicConfig(rules.leaseHoursOptions(), rules.defaultLeaseHours(), rules.pricePerDay(),
                rules.currency(), rules.pickupWindow().toDays(), properties.search().radiusKm(),
                properties.assistancePhone(), properties.demo().enabled());
    }
}
