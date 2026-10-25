package pt.ua.deti.tqs.orbit.web.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.orbit.service.TerminalService;
import pt.ua.deti.tqs.orbit.service.view.TerminalResult;
import pt.ua.deti.tqs.orbit.web.api.dto.CodeRequest;

@RestController
@RequestMapping("/api/v1/terminals/{stationCode}")
@Tag(name = "Locker terminal", description = "UC3 drop-off (courier) and UC4 pickup (recipient) at a station")
public class TerminalApiController {

    private final TerminalService terminal;

    public TerminalApiController(TerminalService terminal) {
        this.terminal = terminal;
    }

    @PostMapping("/drop-offs")
    @Operation(summary = "Courier enters the drop-off PIN; the reserved compartment opens",
            description = "200 with outcome DOOR_OPENED or DOOR_DID_NOT_OPEN (+ replacementAvailable). "
                    + "422 INVALID_CODE (remainingAttempts), 429 TOO_MANY_ATTEMPTS, 409 INVALID_TRANSITION, "
                    + "503 HARDWARE_UNAVAILABLE.")
    public TerminalResult startDropOff(@PathVariable String stationCode, @Valid @RequestBody CodeRequest request) {
        return terminal.startDropOff(stationCode, request.code().trim());
    }

    @PostMapping("/drop-offs/{token}/door-closed")
    @Operation(summary = "Courier closed the door: DEPOSITED (READY_FOR_PICKUP, pickup code sent) or DOOR_DID_NOT_CLOSE")
    public TerminalResult confirmDropOff(@PathVariable String stationCode, @PathVariable String token) {
        return terminal.confirmDropOff(stationCode, token);
    }

    @PostMapping("/drop-offs/{token}/replacement")
    @Operation(summary = "After a door failure, ask for another compartment of the same size",
            description = "REPLACEMENT_ISSUED (new drop-off PIN sent to the sender contact) or NO_REPLACEMENT_AVAILABLE")
    public TerminalResult replacement(@PathVariable String stationCode, @PathVariable String token) {
        return terminal.requestReplacement(stationCode, token);
    }

    @PostMapping("/pickups")
    @Operation(summary = "Recipient enters the pickup PIN; the compartment opens and the parcel is PICKED_UP",
            description = "200 with outcome DOOR_OPENED or DOOR_DID_NOT_OPEN (code stays valid). 422 INVALID_CODE, "
                    + "429 TOO_MANY_ATTEMPTS, 503 HARDWARE_UNAVAILABLE.")
    public TerminalResult startPickup(@PathVariable String stationCode, @Valid @RequestBody CodeRequest request) {
        return terminal.startPickup(stationCode, request.code().trim());
    }

    @PostMapping("/pickups/{token}/door-closed")
    @Operation(summary = "Recipient closed the door: COMPLETED or DOOR_DID_NOT_CLOSE")
    public TerminalResult confirmPickup(@PathVariable String stationCode, @PathVariable String token) {
        return terminal.confirmPickup(stationCode, token);
    }
}
