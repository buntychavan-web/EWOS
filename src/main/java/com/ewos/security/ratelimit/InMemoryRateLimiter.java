package com.ewos.security.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/**
 * Sliding-window rate limiter keyed by client IP. Thread-safe. In-memory only — for a
 * single-instance foundation deployment, this is enough. Horizontal scaling should replace it with
 * a Redis-backed counter; the {@code allow} contract stays the same.
 */
@Component
public class InMemoryRateLimiter {

    private final Map<String, Bucket> hits = new ConcurrentHashMap<>();

    /**
     * @return {@code true} if a hit is allowed, {@code false} if it would exceed {@code
     *     maxAttempts} within the last {@code window}.
     */
    public boolean allow(String key, int maxAttempts, Duration window) {
        if (key == null || key.isBlank() || maxAttempts <= 0 || window == null) {
            return true;
        }
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);
        Bucket bucket = hits.computeIfAbsent(key, k -> new Bucket());
        bucket.lock.lock();
        try {
            while (!bucket.queue.isEmpty() && bucket.queue.peekFirst().isBefore(cutoff)) {
                bucket.queue.pollFirst();
            }
            if (bucket.queue.size() >= maxAttempts) {
                return false;
            }
            bucket.queue.addLast(now);
            return true;
        } finally {
            bucket.lock.unlock();
        }
    }

    /** Wipes state for a key. Useful for tests, and for administrative "unblock this IP" flows. */
    public void reset(String key) {
        hits.remove(key);
    }

    /** Per-key state: a bounded FIFO of hit timestamps + its own lock. */
    private static final class Bucket {
        final Deque<Instant> queue = new ArrayDeque<>();
        final ReentrantLock lock = new ReentrantLock();
    }
}
