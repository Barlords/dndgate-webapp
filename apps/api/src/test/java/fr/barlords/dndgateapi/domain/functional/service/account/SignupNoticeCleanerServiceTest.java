package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.port.out.SignupNoticeLogSpi;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SignupNoticeCleanerServiceTest {

    @Test
    void notices_older_than_the_window_are_forgotten() {
        Instant now = Instant.parse("2026-01-02T03:30:00Z");
        SignupNoticeLogSpi log = mock(SignupNoticeLogSpi.class);

        new SignupNoticeCleanerService(log, Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(24)).purgeExpired();

        verify(log).deleteSentBefore(now.minus(Duration.ofHours(24)));
    }
}
