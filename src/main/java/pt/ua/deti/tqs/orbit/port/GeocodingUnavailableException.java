package pt.ua.deti.tqs.orbit.port;

/** The geocoding provider could not answer (timeout, error, or usage limit). */
public class GeocodingUnavailableException extends RuntimeException {

    /** Why the provider could not answer. */
    public enum Reason {
        /** The provider did not answer in time. */
        TIMEOUT,
        /** The provider failed, was unreachable or returned an invalid response. */
        PROVIDER_ERROR,
        /** The usage limit was reached (locally or by the provider). */
        RATE_LIMITED
    }

    /** Why the provider could not answer. */
    private final Reason reason;

    /**
     * Creates the exception.
     *
     * @param reason  why the provider could not answer
     * @param message explanation
     */
    public GeocodingUnavailableException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * Creates the exception with its cause.
     *
     * @param reason  why the provider could not answer
     * @param message explanation
     * @param cause   the underlying error
     */
    public GeocodingUnavailableException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /**
     * Returns the reason.
     *
     * @return the reason
     */
    public Reason getReason() {
        return reason;
    }
}
