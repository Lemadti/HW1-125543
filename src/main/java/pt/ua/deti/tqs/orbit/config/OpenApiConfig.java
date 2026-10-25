package pt.ua.deti.tqs.orbit.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI (springdoc) metadata for the REST API documentation. */
@Configuration
public class OpenApiConfig {

    /** Creates the OpenAPI configuration. */
    public OpenApiConfig() {
        // no state
    }

    /**
     * Title and description of the OpenAPI document (Swagger UI at {@code /swagger-ui.html}).
     *
     * @return the OpenAPI metadata
     */
    @Bean
    OpenAPI orbitOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Orbit Lockers API")
                .version("v1")
                .description("Smart parcel locker network: stations, parcel registration (partners), tracking, "
                        + "locker terminal, courier and operator endpoints. Errors use RFC 9457 problem details "
                        + "with a stable `code` property."));
    }
}
