package pt.ua.deti.tqs.orbit.web.api;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pt.ua.deti.tqs.orbit.domain.InvalidTransitionException;
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.domain.ParcelStateMachine;
import pt.ua.deti.tqs.orbit.port.GeocodingUnavailableException;

/**
 * Error format for the REST API: RFC 9457 problem details ({@code application/problem+json}) with extra properties
 * {@code code} (stable, machine readable) and {@code timestamp}, plus rule-specific details.
 */
@RestControllerAdvice(basePackageClasses = ApiExceptionHandler.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Creates the handler. */
    public ApiExceptionHandler() {
        // no state
    }

    /**
     * Business rule violations: status from {@link OrbitException.Kind}, plus {@code Retry-After} for lockouts.
     *
     * @param e the exception
     * @return the problem detail
     */
    @ExceptionHandler(OrbitException.class)
    ResponseEntity<ProblemDetail> orbit(OrbitException e) {
        HttpStatus status = switch (e.getKind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case LOCKED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        ProblemDetail pd = problem(status, e.getCode(), e.getMessage());
        e.getDetails().forEach(pd::setProperty);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (e.getKind() == OrbitException.Kind.LOCKED && e.getDetails().get("lockedUntil") instanceof String until) {
            long seconds = Math.max(1, Instant.parse(until).getEpochSecond() - Instant.now().getEpochSecond());
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        }
        return builder.body(pd);
    }

    /**
     * Invalid lifecycle event: 409 with the current state and the allowed events.
     *
     * @param e the exception
     * @return the problem detail
     */
    @ExceptionHandler(InvalidTransitionException.class)
    ResponseEntity<ProblemDetail> invalidTransition(InvalidTransitionException e) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, "INVALID_TRANSITION", e.getMessage());
        pd.setProperty("currentState", e.getCurrentState());
        pd.setProperty("event", e.getEvent());
        pd.setProperty("actor", e.getActor());
        pd.setProperty("allowedEvents", ParcelStateMachine.allowedEvents(e.getCurrentState()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }

    /**
     * Geocoding provider unavailable: 503 with the reason and the fallback endpoint.
     *
     * @param e the exception
     * @return the problem detail
     */
    @ExceptionHandler(GeocodingUnavailableException.class)
    ResponseEntity<ProblemDetail> geocoding(GeocodingUnavailableException e) {
        ProblemDetail pd = problem(HttpStatus.SERVICE_UNAVAILABLE, "GEOCODER_UNAVAILABLE",
                "Address search is temporarily unavailable. Showing all stations instead.");
        pd.setProperty("reason", e.getReason());
        pd.setProperty("fallback", "/api/v1/stations");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, e.getReason() == GeocodingUnavailableException.Reason.RATE_LIMITED ? "1" : "30")
                .body(pd);
    }

    /**
     * Bean validation errors: 400 with a message per field.
     *
     * @param e the exception
     * @return the problem detail
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields are invalid");
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    /**
     * Unreadable body, wrong parameter type or missing parameter: 400.
     *
     * @param e the exception
     * @return the problem detail
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    /**
     * Unreadable body, wrong parameter type or missing parameter: 400.
     *
     * @param e the exception
     * @return the problem detail
     */
    ResponseEntity<ProblemDetail> malformed(Exception e) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request could not be read: " + firstLine(e.getMessage())));
    }

    /**
     * Concurrent modification (unique index, optimistic or pessimistic lock): 409.
     *
     * @param e the exception
     * @return the problem detail
     */
    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class})
    /**
     * Concurrent modification (unique index, optimistic or pessimistic lock): 409.
     *
     * @param e the exception
     * @return the problem detail
     */
    ResponseEntity<ProblemDetail> concurrent(Exception e) {
        log.warn("Concurrent modification rejected: {}", firstLine(e.getMessage()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "The resource was changed by another request. Please retry."));
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setType(URI.create("https://orbit.example/problems/" + code.toLowerCase().replace('_', '-')));
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        List<String> lines = s.lines().toList();
        return lines.isEmpty() ? "" : lines.get(0);
    }
}
