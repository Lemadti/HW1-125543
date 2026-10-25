package pt.ua.deti.tqs.orbit.web.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.service.OperatorService;
import pt.ua.deti.tqs.orbit.service.ParcelService;
import pt.ua.deti.tqs.orbit.service.view.IncidentView;
import pt.ua.deti.tqs.orbit.service.view.ParcelDetail;
import pt.ua.deti.tqs.orbit.service.view.ParcelSummary;
import pt.ua.deti.tqs.orbit.web.api.dto.CompartmentStatusRequest;

@RestController
@RequestMapping("/api/v1/operator")
@Tag(name = "Operator", description = "UC6 - supervise stations")
public class OperatorApiController {

    private final OperatorService operator;
    private final ParcelService parcels;

    public OperatorApiController(OperatorService operator, ParcelService parcels) {
        this.operator = operator;
        this.parcels = parcels;
    }

    @GetMapping("/parcels/recent")
    @Operation(summary = "Latest registered parcels across the network")
    public List<ParcelSummary> recent() {
        return parcels.recent();
    }

    @GetMapping("/parcels/{trackingCode}")
    @Operation(summary = "Full parcel detail (contacts, history, allowed events)")
    public ParcelDetail parcel(@PathVariable String trackingCode) {
        return parcels.detail(trackingCode);
    }

    @GetMapping("/stations/{stationCode}/parcels")
    @Operation(summary = "Parcels of a station, optionally filtered by one or more `state`")
    public List<ParcelSummary> stationParcels(@PathVariable String stationCode,
                                              @RequestParam(name = "state", required = false) List<ParcelState> states) {
        return operator.stationParcels(stationCode, states);
    }

    @GetMapping("/stations/{stationCode}/incidents")
    @Operation(summary = "Hardware malfunctions reported at a station")
    public List<IncidentView> incidents(@PathVariable String stationCode) {
        return operator.incidents(stationCode);
    }

    @PostMapping("/parcels/{trackingCode}/removal")
    @Operation(summary = "Register the removal of an expired parcel (EXPIRED / TO_RETURN -> RETURNED)")
    public ParcelDetail removal(@PathVariable String trackingCode) {
        return operator.registerRemoval(trackingCode);
    }

    @PostMapping("/parcels/{trackingCode}/mark-to-return")
    @Operation(summary = "Flag an expired parcel to be returned (EXPIRED -> TO_RETURN)")
    public ParcelDetail markToReturn(@PathVariable String trackingCode) {
        return operator.markToReturn(trackingCode);
    }

    @PutMapping("/stations/{stationCode}/compartments/{compartmentCode}/status")
    @Operation(summary = "Put a compartment out of service (faulty) or back in service")
    public ResponseEntity<Void> compartmentStatus(@PathVariable String stationCode, @PathVariable String compartmentCode,
                                                  @Valid @RequestBody CompartmentStatusRequest request) {
        operator.setCompartmentStatus(stationCode, compartmentCode, request.status(), request.reason());
        return ResponseEntity.noContent().build();
    }
}
