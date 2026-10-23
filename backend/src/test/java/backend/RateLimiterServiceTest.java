
package backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the in-memory rate limiter.
 */
class RateLimiterServiceTest {

    private RateLimiterService rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiterService();
    }

    @Test
    void freshKeyIsNotBlocked() {
        assertFalse(rateLimiter.isBlocked(
                "login:email:a@example.com", 3, Duration.ofMinutes(15)));
    }

    @Test
    void keyIsBlockedAfterTheLimitIsReached() {
        Duration window = Duration.ofMinutes(15);

        rateLimiter.recordFailure("login:email:b@example.com", window);
        rateLimiter.recordFailure("login:email:b@example.com", window);

        assertFalse(rateLimiter.isBlocked(
                "login:email:b@example.com", 3, window));

        rateLimiter.recordFailure("login:email:b@example.com", window);

        assertTrue(rateLimiter.isBlocked(
                "login:email:b@example.com", 3, window));
    }

    @Test
    void resetClearsTheCounter() {
        Duration window = Duration.ofMinutes(15);

        rateLimiter.recordFailure("login:email:c@example.com", window);
        rateLimiter.recordFailure("login:email:c@example.com", window);
        rateLimiter.recordFailure("login:email:c@example.com", window);

        rateLimiter.reset("login:email:c@example.com");

        assertFalse(rateLimiter.isBlocked(
                "login:email:c@example.com", 3, window));
    }

    @Test
    void differentKeysAreIndependent() {
        Duration window = Duration.ofMinutes(15);

        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure("login:email:d@example.com", window);
        }

        assertTrue(rateLimiter.isBlocked(
                "login:email:d@example.com", 3, window));
        assertFalse(rateLimiter.isBlocked(
                "login:email:e@example.com", 3, window));
    }

    @Test
    void expiredWindowStartsOver() throws InterruptedException {
        Duration window = Duration.ofMillis(60);

        rateLimiter.recordFailure("login:email:f@example.com", window);
        rateLimiter.recordFailure("login:email:f@example.com", window);
        rateLimiter.recordFailure("login:email:f@example.com", window);

        assertTrue(rateLimiter.isBlocked(
                "login:email:f@example.com", 3, window));

        Thread.sleep(120);

        assertFalse(rateLimiter.isBlocked(
                "login:email:f@example.com", 3, window));
    }

    @Test
    void retryAfterIsAlwaysPositive() {
        assertTrue(rateLimiter.retryAfterSeconds(
                "login:email:g@example.com", Duration.ofMinutes(15)) >= 1);
    }
}
