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

    public MutableClock(Clock base) {
        this.base = new AtomicReference<>(base);
    }

    public static MutableClock system() {
        return new MutableClock(Clock.systemUTC());
    }

    public static MutableClock fixedAt(Instant instant) {
        return new MutableClock(Clock.fixed(instant, ZoneOffset.UTC));
    }

    /** Freezes the clock at the given instant (offset reset). */
    public void setFixed(Instant instant) {
        base.set(Clock.fixed(instant, ZoneOffset.UTC));
        offset.set(Duration.ZERO);
    }

    /** Back to real time (offset reset). */
    public void useSystemTime() {
        base.set(Clock.systemUTC());
        offset.set(Duration.ZERO);
    }

    public void advance(Duration amount) {
        offset.updateAndGet(o -> o.plus(amount));
    }

    public void resetOffset() {
        offset.set(Duration.ZERO);
    }

    public Duration getOffset() {
        return offset.get();
    }

    @Override
    public ZoneId getZone() {
        return base.get().getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        MutableClock c = new MutableClock(base.get().withZone(zone));
        c.offset.set(offset.get());
        return c;
    }

    @Override
    public Instant instant() {
        return base.get().instant().plus(offset.get());
    }
}
