package pt.ua.deti.tqs.orbit.web.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.service.RegisterParcelCommand;

/**
 * UC2 request.
 *
 * @param compartmentCode optional specific compartment (e.g. "M-07"); omitted = first free of that size
 * @param leaseStart      optional ISO-8601 instant; omitted = now
 * @param leaseHours      one of the configured options (24, 48, 72); omitted = default (48)
 */
public record RegisterParcelRequest(
        @NotBlank String stationCode,
        @NotNull CompartmentSize size,
        String compartmentCode,
        Instant leaseStart,
        Integer leaseHours,
        @NotNull @Valid ContactRequest sender,
        @NotNull @Valid ContactRequest recipient) {

    public RegisterParcelCommand toCommand(int defaultLeaseHours) {
        return new RegisterParcelCommand(stationCode.trim(), size, compartmentCode, leaseStart,
                leaseHours == null ? defaultLeaseHours : leaseHours, sender.toContact(), recipient.toContact());
    }
}
