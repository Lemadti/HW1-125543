package pt.ua.deti.tqs.orbit.service;

import java.util.List;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.orbit.domain.ParcelState;
import pt.ua.deti.tqs.orbit.domain.Station;
import pt.ua.deti.tqs.orbit.repository.ParcelRepository;

/**
 * Issues PINs that are unique among the parcels that can currently use them at the same station, so that a PIN
 * entered at a terminal identifies exactly one parcel. Drop-off and pickup PINs never coincide for the same parcel.
 */
@Component
public class PinIssuer {

    public record IssuedPin(String pin, String hash) {
    }

    private static final List<ParcelState> DROP_OFF_STATES = List.of(ParcelState.CREATED, ParcelState.ON_ITS_WAY);
    private static final int MAX_TRIES = 20;

    private final CodeService codes;
    private final ParcelRepository parcels;

    public PinIssuer(CodeService codes, ParcelRepository parcels) {
        this.codes = codes;
        this.parcels = parcels;
    }

    public IssuedPin dropOffPin(Station station) {
        for (int i = 0; i < MAX_TRIES; i++) {
            String pin = codes.newPin();
            String hash = codes.hash(pin);
            if (!parcels.existsByStationAndDropOffCodeHashAndStateIn(station, hash, DROP_OFF_STATES)) {
                return new IssuedPin(pin, hash);
            }
        }
        throw new IllegalStateException("Could not issue a unique drop-off PIN");
    }

    public IssuedPin pickupPin(Station station, String dropOffHashToAvoid) {
        for (int i = 0; i < MAX_TRIES; i++) {
            String pin = codes.newPin();
            String hash = codes.hash(pin);
            if (!hash.equals(dropOffHashToAvoid)
                    && !parcels.existsByStationAndPickupCodeHashAndStateIn(station, hash, ParcelState.COLLECTABLE)) {
                return new IssuedPin(pin, hash);
            }
        }
        throw new IllegalStateException("Could not issue a unique pickup PIN");
    }
}
