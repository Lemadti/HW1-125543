package pt.ua.deti.tqs.orbit.port;

/** The station controller did not respond. */
public class HardwareUnavailableException extends RuntimeException {

    public HardwareUnavailableException(String message) {
        super(message);
    }
}
