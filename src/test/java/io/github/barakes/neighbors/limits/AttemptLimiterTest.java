package io.github.barakes.neighbors.limits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class AttemptLimiterTest {

    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-26T20:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TestClock clock = new TestClock();
    private final AttemptLimiter limiter = new AttemptLimiter(3, Duration.ofMinutes(15), clock);

    @Test
    void blocksAfterTheMaximum() {
        for (int i = 0; i < 3; i++) {
            assertFalse(limiter.isBlocked("1.1.1.1"));
            limiter.record("1.1.1.1");
        }
        assertTrue(limiter.isBlocked("1.1.1.1"));
    }

    @Test
    void eachAddressCountsSeparately() {
        for (int i = 0; i < 3; i++) {
            limiter.record("1.1.1.1");
        }
        assertFalse(limiter.isBlocked("2.2.2.2"));
    }

    @Test
    void oldAttemptsStopCounting() {
        for (int i = 0; i < 3; i++) {
            limiter.record("1.1.1.1");
        }
        clock.now = clock.now.plus(Duration.ofMinutes(15));
        assertFalse(limiter.isBlocked("1.1.1.1"));
    }

    @Test
    void theWindowSlides() {
        limiter.record("1.1.1.1");
        clock.now = clock.now.plus(Duration.ofMinutes(10));
        limiter.record("1.1.1.1");
        limiter.record("1.1.1.1");
        assertTrue(limiter.isBlocked("1.1.1.1"));
        // The first attempt leaves the window; the other two are still inside it.
        clock.now = clock.now.plus(Duration.ofMinutes(6));
        assertFalse(limiter.isBlocked("1.1.1.1"));
        limiter.record("1.1.1.1");
        assertTrue(limiter.isBlocked("1.1.1.1"));
    }

    @Test
    void forgettingOldAddressesKeepsTheMapSmall() {
        limiter.record("1.1.1.1");
        limiter.record("2.2.2.2");
        clock.now = clock.now.plus(Duration.ofMinutes(16));
        limiter.record("3.3.3.3");
        limiter.forgetOld();
        assertEquals(1, limiter.size());
    }
}
