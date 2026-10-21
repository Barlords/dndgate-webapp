package fr.barlords.dndgateapi.bootstrap.config;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-window counter per key. In memory: each instance of the API counts on its own.
 */
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(long startMillis, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Counts one request for the key.
     *
     * @return 0 if the request is allowed, otherwise the number of seconds to wait before retrying
     */
    public long tryAcquire(String key, int max, Duration window) {
        long now = clock.millis();
        long windowMillis = window.toMillis();
        AtomicLong retryAfterSeconds = new AtomicLong(0);

        windows.compute(key, (k, current) -> {
            if (current == null || now - current.startMillis() >= windowMillis) {
                return new Window(now, 1);
            }
            if (current.count() >= max) {
                long remainingMillis = current.startMillis() + windowMillis - now;
                retryAfterSeconds.set(Math.max(1, (remainingMillis + 999) / 1000));
                return current;
            }
            return new Window(current.startMillis(), current.count() + 1);
        });

        if (windows.size() > CLEANUP_THRESHOLD) {
            // windows are all at most as long as the longest rule, one hour
            windows.values().removeIf(w -> now - w.startMillis() >= Duration.ofHours(1).toMillis());
        }

        return retryAfterSeconds.get();
    }

}
