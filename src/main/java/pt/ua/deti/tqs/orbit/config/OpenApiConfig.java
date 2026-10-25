package pt.ua.deti.tqs.orbit.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

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
