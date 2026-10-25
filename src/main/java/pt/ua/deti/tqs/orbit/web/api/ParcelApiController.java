package pt.ua.deti.tqs.orbit.web.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.domain.Actor;
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.service.ParcelService;
import pt.ua.deti.tqs.orbit.service.view.RegistrationResult;
import pt.ua.deti.tqs.orbit.service.view.TrackingView;
import pt.ua.deti.tqs.orbit.web.api.dto.CancelRequest;
import pt.ua.deti.tqs.orbit.web.api.dto.LocationRequest;
import pt.ua.deti.tqs.orbit.web.api.dto.RegisterParcelRequest;

@RestController
@RequestMapping("/api/v1/parcels")
@Tag(name = "Parcels", description = "UC2 register (partner), UC5 track (partner / recipient)")
public class ParcelApiController {

    private final ParcelService parcels;
    private final OrbitProperties properties;

    public ParcelApiController(ParcelService parcels, OrbitProperties properties) {
        this.parcels = parcels;
        this.properties = properties;
    }

    @PostMapping
    @Operation(summary = "Register a parcel and reserve a compartment",
            description = "201 with the tracking code, the delivery code (QR) and the drop-off PIN to print on the "
                    + "label. 409 STATION_FULL / COMPARTMENT_NOT_AVAILABLE, 422 for rule violations.")
    public ResponseEntity<RegistrationResult> register(@Valid @RequestBody RegisterParcelRequest request) {
        RegistrationResult result = parcels.register(request.toCommand(properties.rules().defaultLeaseHours()));
        return ResponseEntity.created(URI.create("/api/v1/parcels/" + result.trackingCode())).body(result);
    }

    @GetMapping("/{trackingCode}")
    @Operation(summary = "Track a parcel: current state and timestamped history")
    public TrackingView track(@PathVariable String trackingCode) {
        return parcels.track(trackingCode);
    }

    @PostMapping("/{trackingCode}/cancel")
    @Operation(summary = "Cancel a parcel not yet collected by the courier (CREATED -> CANCELLED)")
    public TrackingView cancel(@PathVariable String trackingCode, @Valid @RequestBody(required = false) CancelRequest request) {
        Actor actor = request == null || request.actor() == null ? Actor.PARTNER : request.actor();
        if (actor != Actor.PARTNER && actor != Actor.RECIPIENT) {
            throw OrbitException.unprocessable("INVALID_ACTOR", "Only the partner or the recipient can cancel");
        }
        return parcels.cancel(trackingCode, actor, request == null ? null : request.reason());
    }

    @PostMapping("/{trackingCode}/locations")
    @Operation(summary = "Courier reports the current location of a parcel ON_ITS_WAY")
    public TrackingView addLocation(@PathVariable String trackingCode, @Valid @RequestBody LocationRequest request) {
        return parcels.addLocation(trackingCode, request.label(), request.latitude(), request.longitude());
    }
}
