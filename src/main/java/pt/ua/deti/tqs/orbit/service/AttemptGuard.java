package pt.ua.deti.tqs.orbit.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.orbit.config.OrbitProperties;
import pt.ua.deti.tqs.orbit.domain.OrbitException;

/**
 * Protection against guessing PINs at a terminal: after {@code maxFailedAttempts} consecutive wrong codes for the same
 * key (station + code type) the key is blocked for {@code lockout}. A correct code resets the counter.
 */
@Component
public class AttemptGuard {

    private static final Logger log = LoggerFactory.getLogger(AttemptGuard.class);

    private record State(int failures, Instant lockedUntil) {
    }

    private final Clock clock;
    private final int maxFailedAttempts;
    private final Duration lockout;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    /**
     * Creates the guard from {@code orbit.codes.max-failed-attempts} and {@code orbit.codes.lockout}.
     *
     * @param clock      time source
     * @param properties business rules
     */
    @Autowired
    public AttemptGuard(Clock clock, OrbitProperties properties) {
        this(clock, properties.codes().maxFailedAttempts(), properties.codes().lockout());
    }

    /**
     * Creates the guard with explicit limits (tests).
     *
     * @param clock             time source
     * @param maxFailedAttempts consecutive failures that trigger the lockout
     * @param lockout           how long the key stays blocked
     */
    AttemptGuard(Clock clock, int maxFailedAttempts, Duration lockout) {
        this.clock = clock;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockout = lockout;
    }

    /**
     * Fails if the key is currently blocked; clears an expired lockout.
     *
     * @param key station code and code type, e.g. "ORB-AVR-001:PICK_UP"
     * @throws OrbitException (LOCKED) if the key is blocked
     */
    public void ensureNotLocked(String key) {
        State s = states.get(key);
        Instant now = clock.instant();
        if (s != null && s.lockedUntil() != null) {
            if (now.isBefore(s.lockedUntil())) {
                throw locked(s.lockedUntil());
            }
            states.remove(key);
        }
    }

    /**
     * Records a wrong code.
     *
     * @param key station code and code type
     * @return remaining attempts before the lockout
     * @throws OrbitException (LOCKED) if this failure triggered the lockout
     */
    public int recordFailure(String key) {
        Instant now = clock.instant();
        State s = states.compute(key, (k, old) -> {
            int failures = (old == null || old.lockedUntil() != null ? 0 : old.failures()) + 1;
            return failures >= maxFailedAttempts ? new State(failures, now.plus(lockout)) : new State(failures, null);
        });
        if (s.lockedUntil() != null) {
            log.warn("Code entry blocked for {} until {} after {} wrong attempts", key, s.lockedUntil(), s.failures());
            throw locked(s.lockedUntil());
        }
        return maxFailedAttempts - s.failures();
    }

    /**
     * Resets the counter after a correct code.
     *
     * @param key station code and code type
     */
    public void recordSuccess(String key) {
        states.remove(key);
    }

    /** Clears every counter and lockout (demo and tests). */
    public void resetAll() {
        states.clear();
    }

    private static OrbitException locked(Instant until) {
        return new OrbitException(OrbitException.Kind.LOCKED, "TOO_MANY_ATTEMPTS",
                "Too many wrong codes. Code entry is blocked until " + until + ".",
                Map.of("lockedUntil", until.toString()));
    }
}
