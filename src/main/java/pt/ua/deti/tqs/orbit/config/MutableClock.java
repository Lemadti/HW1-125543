package pt.ua.deti.tqs.orbit.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock = base clock + adjustable offset. The application always uses it with the system clock as base and a zero
 * offset; tests fix the base and move it forward, and the demo page can fast-forward through deadlines.
 */
public class MutableClock extends Clock {

    private final AtomicReference<Clock> base;
    private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

    /**
     * Creates a clock following the given base with no offset.
     *
     * @param base the base clock
     */
    public MutableClock(Clock base) {
        this.base = new AtomicReference<>(base);
    }

    /**
     * A clock on system time (UTC) with no offset.
     *
     * @return the clock
     */
    public static MutableClock system() {
        return new MutableClock(Clock.systemUTC());
    }

    /**
     * A clock frozen at the given instant.
     *
     * @param instant the fixed time
     * @return the clock
     */
    public static MutableClock fixedAt(Instant instant) {
        return new MutableClock(Clock.fixed(instant, ZoneOffset.UTC));
    }

    /**
     * Freezes the clock at the given instant and resets the offset.
     *
     * @param instant the fixed time
     */
    public void setFixed(Instant instant) {
        base.set(Clock.fixed(instant, ZoneOffset.UTC));
        offset.set(Duration.ZERO);
    }

    /** Back to real time (offset reset). */
    public void useSystemTime() {
        base.set(Clock.systemUTC());
        offset.set(Duration.ZERO);
    }

    /**
     * Moves the clock forward.
     *
     * @param amount how much to add to the offset
     */
    public void advance(Duration amount) {
        offset.updateAndGet(o -> o.plus(amount));
    }

    /** Removes the offset, keeping the base clock. */
    public void resetOffset() {
        offset.set(Duration.ZERO);
    }

    /**
     * Returns the offset.
     *
     * @return the offset
     */
    public Duration getOffset() {
        return offset.get();
    }

    /**
     * Returns the zone.
     *
     * @return the zone
     */
    @Override
    public ZoneId getZone() {
        return base.get().getZone();
    }

    /**
     * A copy of this clock in another zone, with the same offset.
     *
     * @param zone the zone
     * @return the copy
     */
    @Override
    public Clock withZone(ZoneId zone) {
        MutableClock c = new MutableClock(base.get().withZone(zone));
        c.offset.set(offset.get());
        return c;
    }

    /**
     * Base time plus offset.
     *
     * @return the current instant
     */
    @Override
    public Instant instant() {
        return base.get().instant().plus(offset.get());
    }
}
