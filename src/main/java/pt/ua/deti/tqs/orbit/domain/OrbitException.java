package pt.ua.deti.tqs.orbit.domain;

import java.util.Map;

/**
 * Business rule violation with a stable machine-readable code. The web layer maps the {@link Kind} to an HTTP status.
 */
public class OrbitException extends RuntimeException {

    /** Category of the violation, mapped to an HTTP status by the web layer. */
    public enum Kind {
        /** The resource does not exist (HTTP 404). */
        NOT_FOUND,
        /** The request conflicts with the current state (HTTP 409). */
        CONFLICT,
        /** The request breaks a business rule (HTTP 422). */
        UNPROCESSABLE,
        /** Code entry is temporarily blocked (HTTP 429). */
        LOCKED,
        /** A dependency, such as the locker hardware, is not responding (HTTP 503). */
        UNAVAILABLE
    }

    /** Category of the violation. */
    private final Kind kind;
    /** Stable machine-readable code. */
    private final String code;
    /** Extra properties for the error body. */
    private final transient Map<String, Object> details;

    /**
     * Creates an exception without extra details.
     *
     * @param kind    category
     * @param code    stable machine-readable code, e.g. STATION_FULL
     * @param message human-readable explanation
     */
    public OrbitException(Kind kind, String code, String message) {
        this(kind, code, message, Map.of());
    }

    /**
     * Creates an exception with extra details that are added to the error body.
     *
     * @param kind    category
     * @param code    stable machine-readable code
     * @param message human-readable explanation
     * @param details extra properties, e.g. remainingAttempts
     */
    public OrbitException(Kind kind, String code, String message, Map<String, Object> details) {
        super(message);
        this.kind = kind;
        this.code = code;
        this.details = details;
    }

    /**
     * Shortcut for a {@link Kind#NOT_FOUND} exception.
     *
     * @param code    machine-readable code
     * @param message explanation
     * @return the exception
     */
    public static OrbitException notFound(String code, String message) {
        return new OrbitException(Kind.NOT_FOUND, code, message);
    }

    /**
     * Shortcut for a {@link Kind#CONFLICT} exception.
     *
     * @param code    machine-readable code
     * @param message explanation
     * @return the exception
     */
    public static OrbitException conflict(String code, String message) {
        return new OrbitException(Kind.CONFLICT, code, message);
    }

    /**
     * Shortcut for a {@link Kind#UNPROCESSABLE} exception.
     *
     * @param code    machine-readable code
     * @param message explanation
     * @return the exception
     */
    public static OrbitException unprocessable(String code, String message) {
        return new OrbitException(Kind.UNPROCESSABLE, code, message);
    }

    /**
     * Returns the kind.
     *
     * @return the kind
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * Returns the code.
     *
     * @return the code
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the details.
     *
     * @return the details
     */
    public Map<String, Object> getDetails() {
        return details;
    }
}
