package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.account.InvalidRefreshTokenException;
import fr.barlords.dndgateapi.domain.functional.model.account.AccessToken;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.AccountSession;
import fr.barlords.dndgateapi.domain.functional.model.account.IssuedRefreshToken;
import fr.barlords.dndgateapi.domain.functional.model.account.RefreshToken;
import fr.barlords.dndgateapi.domain.functional.model.account.Session;
import fr.barlords.dndgateapi.domain.port.out.AccessRevocationSpi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.OpaqueTokenSpi;
import fr.barlords.dndgateapi.domain.port.out.SessionPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountSessionRefresherServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private AccountPersistenceSpi accounts;

    @Mock
    private SessionPersistenceSpi sessions;

    @Mock
    private OpaqueTokenSpi opaqueTokens;

    @Mock
    private SessionIssuerService sessionIssuer;

    @Mock
    private AccessRevocationSpi accessRevocation;

    private AccountSessionRefresherService service;

    private final Account account = Account.builder().pseudo("usertest").email("test@test.fr").activated(true).build();

    private final Session session = Session.builder()
            .accountId(account.getId()).createdAt(NOW.minusSeconds(3600)).expiresAt(NOW.plusSeconds(86400)).build();

    private RefreshToken stored(Instant expiresAt, Instant usedAt) {
        return RefreshToken.builder().sessionId(session.getId()).tokenHash("hashed").expiresAt(expiresAt).usedAt(usedAt).build();
    }

    @BeforeEach
    void setUp() {
        service = new AccountSessionRefresherService(accounts, sessions, opaqueTokens, sessionIssuer,
                Clock.fixed(NOW, ZoneOffset.UTC), accessRevocation);
        when(opaqueTokens.hash("secret")).thenReturn("hashed");
    }

    @Test
    void an_unknown_refresh_token_is_refused() {
        when(sessions.findRefreshTokenByHash("hashed")).thenReturn(Optional.empty());

        Assertions.assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("secret"));
    }

    @Test
    void a_valid_refresh_token_is_consumed_and_replaced() {
        RefreshToken token = stored(NOW.plusSeconds(600), null);
        AccountSession renewed = new AccountSession(account, new AccessToken("new-jwt", NOW), new IssuedRefreshToken("new-secret", NOW));
        when(sessions.findRefreshTokenByHash("hashed")).thenReturn(Optional.of(token));
        when(sessions.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(sessions.markRefreshTokenUsed(token.getId(), NOW)).thenReturn(true);
        when(sessionIssuer.issueTokens(account, session)).thenReturn(renewed);

        Assertions.assertEquals(renewed, service.refresh("secret"));
    }

    @Test
    void an_expired_refresh_token_is_refused() {
        RefreshToken token = stored(NOW.minusSeconds(1), null);
        when(sessions.findRefreshTokenByHash("hashed")).thenReturn(Optional.of(token));
        when(sessions.findSessionById(session.getId())).thenReturn(Optional.of(session));

        Assertions.assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("secret"));

        verify(sessions, never()).markRefreshTokenUsed(any(), any());
    }

    @Test
    void reusing_a_refresh_token_revokes_the_whole_session() {
        RefreshToken alreadyUsed = stored(NOW.plusSeconds(600), NOW.minusSeconds(30));
        when(sessions.findRefreshTokenByHash("hashed")).thenReturn(Optional.of(alreadyUsed));
        when(sessions.findSessionById(session.getId())).thenReturn(Optional.of(session));

        Assertions.assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("secret"));

        verify(sessions).revokeSession(session.getId(), NOW);
        verify(accessRevocation).sessionRevoked(session.getId());
        verify(sessionIssuer, never()).issueTokens(any(), any());
    }

    @Test
    void a_concurrent_use_of_the_same_token_is_treated_as_a_reuse() {
        RefreshToken token = stored(NOW.plusSeconds(600), null);
        when(sessions.findRefreshTokenByHash("hashed")).thenReturn(Optional.of(token));
        when(sessions.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(sessions.markRefreshTokenUsed(token.getId(), NOW)).thenReturn(false);

        Assertions.assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("secret"));

        verify(sessions).revokeSession(session.getId(), NOW);
        verify(accessRevocation).sessionRevoked(session.getId());
        verify(sessionIssuer, never()).issueTokens(any(), any());
    }

    @Test
    void a_revoked_session_cannot_be_refreshed() {
        Session revoked = session.withRevokedAt(NOW.minusSeconds(10));
        when(sessions.findRefreshTokenByHash("hashed")).thenReturn(Optional.of(stored(NOW.plusSeconds(600), null)));
        when(sessions.findSessionById(session.getId())).thenReturn(Optional.of(revoked));

        Assertions.assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("secret"));

        verify(sessions, never()).markRefreshTokenUsed(any(), any());
    }

    @Test
    void an_expired_session_cannot_be_refreshed() {
        Session expired = session.withExpiresAt(NOW.minusSeconds(1));
        when(sessions.findRefreshTokenByHash("hashed")).thenReturn(Optional.of(stored(NOW.plusSeconds(600), null)));
        when(sessions.findSessionById(session.getId())).thenReturn(Optional.of(expired));

        Assertions.assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("secret"));
    }

    @Test
    void a_deleted_or_not_activated_account_cannot_refresh() {
        when(sessions.findRefreshTokenByHash("hashed")).thenReturn(Optional.of(stored(NOW.plusSeconds(600), null)));
        when(sessions.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(accounts.findById(account.getId())).thenReturn(Optional.empty());

        Assertions.assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("secret"));

        verify(sessions, never()).markRefreshTokenUsed(any(), any());
    }
}
