package pt.ua.deti.tqs.orbit.domain;

import java.util.Map;

/**
 * Business rule violation with a stable machine-readable code. The web layer maps the {@link Kind} to an HTTP status.
 */
public class OrbitException extends RuntimeException {

    public enum Kind {
        NOT_FOUND,
        CONFLICT,
        UNPROCESSABLE,
        LOCKED,
        UNAVAILABLE
    }

    private final Kind kind;
    private final String code;
    private final transient Map<String, Object> details;

    public OrbitException(Kind kind, String code, String message) {
        this(kind, code, message, Map.of());
    }

    public OrbitException(Kind kind, String code, String message, Map<String, Object> details) {
        super(message);
        this.kind = kind;
        this.code = code;
        this.details = details;
    }

    public static OrbitException notFound(String code, String message) {
        return new OrbitException(Kind.NOT_FOUND, code, message);
    }

    public static OrbitException conflict(String code, String message) {
        return new OrbitException(Kind.CONFLICT, code, message);
    }

    public static OrbitException unprocessable(String code, String message) {
        return new OrbitException(Kind.UNPROCESSABLE, code, message);
    }

    public Kind getKind() {
        return kind;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
