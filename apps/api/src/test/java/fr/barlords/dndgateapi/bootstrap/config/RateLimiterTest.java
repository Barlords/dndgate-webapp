package fr.barlords.dndgateapi.bootstrap.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

class RateLimiterTest {

    private static class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

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

    private final MutableClock clock = new MutableClock();

    private final RateLimiter limiter = new RateLimiter(clock);

    private static final Duration MINUTE = Duration.ofMinutes(1);

    @Test
    void requests_under_the_limit_are_allowed() {
        for (int i = 0; i < 3; i++) {
            Assertions.assertEquals(0, limiter.tryAcquire("login:1.2.3.4", 3, MINUTE));
        }
    }

    @Test
    void requests_over_the_limit_are_refused_with_the_time_to_wait() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("login:1.2.3.4", 3, MINUTE);
        }
        clock.advance(Duration.ofSeconds(20));

        Assertions.assertEquals(40, limiter.tryAcquire("login:1.2.3.4", 3, MINUTE));
    }

    @Test
    void the_counter_starts_again_when_the_window_is_over() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("login:1.2.3.4", 3, MINUTE);
        }
        clock.advance(MINUTE);

        Assertions.assertEquals(0, limiter.tryAcquire("login:1.2.3.4", 3, MINUTE));
    }

    @Test
    void each_key_has_its_own_counter() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("login:1.2.3.4", 3, MINUTE);
        }

        Assertions.assertTrue(limiter.tryAcquire("login:1.2.3.4", 3, MINUTE) > 0);
        Assertions.assertEquals(0, limiter.tryAcquire("login:5.6.7.8", 3, MINUTE));
        Assertions.assertEquals(0, limiter.tryAcquire("refresh:1.2.3.4", 3, MINUTE));
    }
}
