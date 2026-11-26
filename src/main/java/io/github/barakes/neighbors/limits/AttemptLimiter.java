package io.github.barakes.neighbors.limits;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Counts attempts per address in a sliding window: "at most 10 in any 15 minutes".
 * Kept in memory only; a restart starts over, which is fine for this.
 */
public class AttemptLimiter {

    private final int max;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new HashMap<>();

    public AttemptLimiter(int max, Duration window, Clock clock) {
        this.max = max;
        this.window = window;
        this.clock = clock;
    }

    public synchronized boolean isBlocked(String address) {
        return recent(address).size() >= max;
    }

    public synchronized void record(String address) {
        recent(address).addLast(clock.instant());
    }

    /** Drops addresses with no recent attempts, so the map doesn't grow forever. */
    public synchronized void forgetOld() {
        attempts.keySet().removeIf(address -> recent(address).isEmpty());
    }

    synchronized int size() {
        return attempts.size();
    }

    /** The attempts of this address inside the window; older ones are dropped on the way. */
    private Deque<Instant> recent(String address) {
        Deque<Instant> times = attempts.computeIfAbsent(address, key -> new ArrayDeque<>());
        Instant limit = clock.instant().minus(window);
        while (!times.isEmpty() && !times.peekFirst().isAfter(limit)) {
            times.pollFirst();
        }
        return times;
    }
}
