package com.nutritrack.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Small in-memory brute-force guard: after {@value #MAX_FAILURES} failed logins for the same email within
 * 15 minutes, further attempts are refused until the window passes. For multi-instance deployments this
 * should move to a shared store.
 */
@Service
public class LoginAttemptService {
    static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String key) {
        Attempts a = attempts.get(key);
        if (a == null) {
            return false;
        }
        if (a.windowStart.plus(WINDOW).isBefore(clock.instant())) {
            attempts.remove(key);
            return false;
        }
        return a.failures >= MAX_FAILURES;
    }

    public void recordFailure(String key) {
        Instant now = clock.instant();
        attempts.compute(key, (k, a) -> a == null || a.windowStart.plus(WINDOW).isBefore(now)
                ? new Attempts(now, 1)
                : new Attempts(a.windowStart, a.failures + 1));
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private record Attempts(Instant windowStart, int failures) {
    }
}
