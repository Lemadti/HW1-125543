package pt.ua.deti.tqs.orbit.web.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pt.ua.deti.tqs.orbit.domain.CompartmentSize;
import pt.ua.deti.tqs.orbit.service.RegisterParcelCommand;

/**
 * Body of {@code POST /api/v1/parcels} (UC2).
 *
 * @param stationCode     station code (required)
 * @param size            parcel size S, M or L (required)
 * @param compartmentCode optional specific compartment, e.g. "M-07"; omitted means the first free of that size
 * @param sender          sender contact (required)
 * @param recipient       recipient contact (required)
 */
public record RegisterParcelRequest(
        @NotBlank String stationCode,
        @NotNull CompartmentSize size,
        String compartmentCode,
        @NotNull @Valid ContactRequest sender,
        @NotNull @Valid ContactRequest recipient) {

    /**
     * Converts to the service command.
     *
     * @return the command
     */
    public RegisterParcelCommand toCommand() {
        return new RegisterParcelCommand(stationCode.trim(), size, compartmentCode, sender.toContact(),
                recipient.toContact());
    }
}
