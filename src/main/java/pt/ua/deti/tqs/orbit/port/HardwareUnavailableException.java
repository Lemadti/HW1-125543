package pt.ua.deti.tqs.orbit.port;

/** The station controller did not respond. */
public class HardwareUnavailableException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message explanation
     */
    public HardwareUnavailableException(String message) {
        super(message);
    }
}
