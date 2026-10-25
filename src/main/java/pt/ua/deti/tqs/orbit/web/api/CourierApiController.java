package pt.ua.deti.tqs.orbit.web.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.deti.tqs.orbit.service.ParcelService;
import pt.ua.deti.tqs.orbit.service.view.TrackingView;
import pt.ua.deti.tqs.orbit.web.api.dto.CollectRequest;

/** REST endpoint used by couriers when collecting a parcel at the partner. */
@RestController
@RequestMapping("/api/v1/courier")
@Tag(name = "Courier", description = "Collecting parcels at the partner")
public class CourierApiController {

    private final ParcelService parcels;

    /**
     * Creates the controller.
     *
     * @param parcels parcel use cases
     */
    public CourierApiController(ParcelService parcels) {
        this.parcels = parcels;
    }

    /**
     * {@code POST /api/v1/courier/collections}: the courier scans the delivery code (CREATED to ON_ITS_WAY).
     *
     * @param request delivery code and optional location
     * @return the updated tracking view
     */
    @PostMapping("/collections")
    @Operation(summary = "Scan the delivery code (QR) of a box: CREATED -> ON_ITS_WAY")
    public TrackingView collect(@Valid @RequestBody CollectRequest request) {
        return parcels.collect(request.deliveryCode(), request.locationLabel());
    }
}
