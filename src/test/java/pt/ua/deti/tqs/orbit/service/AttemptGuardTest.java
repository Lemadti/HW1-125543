package pt.ua.deti.tqs.orbit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.orbit.config.MutableClock;
import pt.ua.deti.tqs.orbit.domain.OrbitException;
import pt.ua.deti.tqs.orbit.support.TestData;

/** Protection against guessing: time is moved with a MutableClock, never with sleep. */
class AttemptGuardTest {

    private final MutableClock clock = MutableClock.fixedAt(TestData.T0);
    private final AttemptGuard guard = new AttemptGuard(clock, 3, Duration.ofHours(1));

    @Test
    void countsDownRemainingAttempts() {
        assertThat(guard.recordFailure("k")).isEqualTo(2);
        assertThat(guard.recordFailure("k")).isEqualTo(1);
    }

    @Test
    void thirdConsecutiveFailureBlocksForOneHour() {
        guard.recordFailure("k");
        guard.recordFailure("k");
        assertThatThrownBy(() -> guard.recordFailure("k"))
                .isInstanceOfSatisfying(OrbitException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(OrbitException.Kind.LOCKED);
                    assertThat(e.getDetails()).containsEntry("lockedUntil", TestData.T0.plus(Duration.ofHours(1)).toString());
                });

        clock.advance(Duration.ofMinutes(59));
        assertThatThrownBy(() -> guard.ensureNotLocked("k")).isInstanceOf(OrbitException.class);

        clock.advance(Duration.ofMinutes(1));
        assertThatNoException().isThrownBy(() -> guard.ensureNotLocked("k"));
        assertThat(guard.recordFailure("k")).isEqualTo(2); // counter restarted
    }

    @Test
    void successResetsTheCounter() {
        guard.recordFailure("k");
        guard.recordFailure("k");
        guard.recordSuccess("k");
        assertThat(guard.recordFailure("k")).isEqualTo(2);
    }

    @Test
    void keysAreIndependent() {
        guard.recordFailure("a");
        guard.recordFailure("a");
        assertThat(guard.recordFailure("b")).isEqualTo(2);
    }
}
