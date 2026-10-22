package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.port.out.PendingSignupPersistenceSpi;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PendingSignupCleanerServiceTest {

    @Test
    void pending_signups_that_expired_before_now_are_forgotten() {
        Instant now = Instant.parse("2026-01-02T03:30:00Z");
        PendingSignupPersistenceSpi pendingSignups = mock(PendingSignupPersistenceSpi.class);

        new PendingSignupCleanerService(pendingSignups, Clock.fixed(now, ZoneOffset.UTC)).purgeExpired();

        verify(pendingSignups).deleteExpiredBefore(now);
    }
}
