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

    /**
     * A PIN and its HMAC.
     *
     * @param pin  the PIN in clear (returned or sent once, never stored)
     * @param hash its HMAC, stored on the parcel
     */
    public record IssuedPin(String pin, String hash) {
    }

    private static final List<ParcelState> DROP_OFF_STATES = List.of(ParcelState.CREATED, ParcelState.ON_ITS_WAY);
    private static final int MAX_TRIES = 20;

    private final CodeService codes;
    private final ParcelRepository parcels;

    /**
     * Creates the issuer.
     *
     * @param codes   generates and hashes PINs
     * @param parcels checks uniqueness per station
     */
    public PinIssuer(CodeService codes, ParcelRepository parcels) {
        this.codes = codes;
        this.parcels = parcels;
    }

    /**
     * Issues a drop-off PIN unique among the station's CREATED and ON_ITS_WAY parcels.
     *
     * @param station the station
     * @return the PIN and its hash
     */
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

    /**
     * Issues a pickup PIN unique among the station's collectable parcels and different from the drop-off PIN.
     *
     * @param station            the station
     * @param dropOffHashToAvoid hash of the parcel's drop-off PIN
     * @return the PIN and its hash
     */
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
