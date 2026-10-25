package pt.ua.deti.tqs.orbit.port;

/** The geocoding provider could not answer (timeout, error, or usage limit). */
public class GeocodingUnavailableException extends RuntimeException {

    public enum Reason {
        TIMEOUT,
        PROVIDER_ERROR,
        RATE_LIMITED
    }

    private final Reason reason;

    public GeocodingUnavailableException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public GeocodingUnavailableException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
