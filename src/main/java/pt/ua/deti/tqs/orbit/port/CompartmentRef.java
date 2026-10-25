package pt.ua.deti.tqs.orbit.port;

/**
 * Identifies a physical compartment for the hardware port.
 *
 * @param stationCode     station code, e.g. ORB-AVR-001
 * @param compartmentCode compartment code, e.g. M-07
 */
public record CompartmentRef(String stationCode, String compartmentCode) {

    /**
     * Short form used in logs.
     *
     * @return "station/compartment"
     */
    @Override
    public String toString() {
        return stationCode + "/" + compartmentCode;
    }
}
