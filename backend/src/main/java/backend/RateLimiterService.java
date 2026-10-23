
package backend;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Small in-memory rate limiter used to slow down brute force attacks on the
 * login, forgot-password and reset-password endpoints.
 *
 * A fixed window counter is kept per key (for example
 * {@code login:email:someone@example.com}). The limiter is intentionally
 * dependency free and process local: it protects a single instance and is
 * good enough to make online guessing impractical. A shared store (Redis)
 * would be required for a multi instance deployment.
 */
@Service
public class RateLimiterService {

    private static final class Counter {
        private volatile long windowStart;
        private volatile int count;
    }

    /** Purge threshold so the map cannot grow without bound. */
    private static final int PURGE_THRESHOLD = 5000;

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    /**
     * Checks whether the key has already exhausted its allowance. The
     * counter is not incremented by this call.
     */
    public boolean isBlocked(String key, int maxAttempts, Duration window) {
        if (key == null || maxAttempts <= 0) {
            return false;
        }

        Counter counter = counters.get(key);

        if (counter == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (now - counter.windowStart >= window.toMillis()) {
            return false;
        }

        return counter.count >= maxAttempts;
    }

    /** Records a failed attempt against the key. */
    public void recordFailure(String key, Duration window) {
        if (key == null) {
            return;
        }

        maybePurge();

        long now = System.currentTimeMillis();

        counters.compute(key, (ignored, existing) -> {
            if (existing == null
                    || now - existing.windowStart >= window.toMillis()) {
                Counter fresh = new Counter();
                fresh.windowStart = now;
                fresh.count = 1;
                return fresh;
            }

            existing.count = existing.count + 1;
            return existing;
        });
    }

    /** Clears the counter, for example after a successful sign-in. */
    public void reset(String key) {
        if (key != null) {
            counters.remove(key);
        }
    }

    /**
     * Seconds the caller should wait before retrying. Always at least one
     * second so clients receive a meaningful {@code Retry-After} value.
     */
    public long retryAfterSeconds(String key, Duration window) {
        Counter counter = counters.get(key);

        if (counter == null) {
            return 1;
        }

        long elapsed = System.currentTimeMillis() - counter.windowStart;
        long remaining = window.toMillis() - elapsed;

        if (remaining <= 0) {
            return 1;
        }

        return Math.max(1, (remaining + 999) / 1000);
    }

    private void maybePurge() {
        if (counters.size() < PURGE_THRESHOLD) {
            return;
        }

        long now = System.currentTimeMillis();

        Iterator<Map.Entry<String, Counter>> iterator =
                counters.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Counter> entry = iterator.next();
            long age = now - entry.getValue().windowStart;

            if (age > Duration.ofHours(2).toMillis()) {
                iterator.remove();
            }
        }
    }
}
