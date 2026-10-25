package pt.ua.deti.tqs.orbit.port;

/** Identifies a physical compartment for the hardware port. */
public record CompartmentRef(String stationCode, String compartmentCode) {

    @Override
    public String toString() {
        return stationCode + "/" + compartmentCode;
    }
}
