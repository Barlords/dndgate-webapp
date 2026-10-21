package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.port.out.AccessRevocationSpi;
import fr.barlords.dndgateapi.domain.port.out.SessionPersistenceSpi;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/** Ending a session also ends what was opened with it: the live channels are told, after the session itself. */
class AccountSessionRevokerServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final SessionPersistenceSpi sessions = mock(SessionPersistenceSpi.class);

    private final AccessRevocationSpi accessRevocation = mock(AccessRevocationSpi.class);

    private final AccountSessionRevokerService service =
            new AccountSessionRevokerService(sessions, Clock.fixed(NOW, ZoneOffset.UTC), accessRevocation);

    @Test
    void logging_out_revokes_the_session_and_cuts_what_it_opened() {
        UUID sessionId = UUID.randomUUID();

        service.logout(sessionId);

        InOrder order = inOrder(sessions, accessRevocation);
        order.verify(sessions).revokeSession(sessionId, NOW);
        order.verify(accessRevocation).sessionRevoked(sessionId);
        verifyNoMoreInteractions(accessRevocation);
    }

    @Test
    void logging_out_everywhere_revokes_every_session_and_cuts_every_channel_of_the_account() {
        UUID accountId = UUID.randomUUID();

        service.logoutAll(accountId);

        InOrder order = inOrder(sessions, accessRevocation);
        order.verify(sessions).revokeAllSessionsOfAccount(accountId, NOW);
        order.verify(accessRevocation).allSessionsRevoked(accountId);
        verifyNoMoreInteractions(accessRevocation);
    }
}
