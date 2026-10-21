package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.model.account.AccessToken;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.AccountSession;
import fr.barlords.dndgateapi.domain.functional.model.account.RefreshToken;
import fr.barlords.dndgateapi.domain.functional.model.account.Session;
import fr.barlords.dndgateapi.domain.port.out.AccessTokenSpi;
import fr.barlords.dndgateapi.domain.port.out.OpaqueTokenSpi;
import fr.barlords.dndgateapi.domain.port.out.SessionPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SessionIssuerServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private SessionPersistenceSpi sessions;

    @Mock
    private AccessTokenSpi accessTokens;

    @Mock
    private OpaqueTokenSpi opaqueTokens;

    private SessionIssuerService service;

    private final Account account = Account.builder().pseudo("usertest").email("test@test.fr").build();

    @BeforeEach
    void setUp() {
        service = new SessionIssuerService(sessions, accessTokens, opaqueTokens, Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofDays(7), Duration.ofDays(30));
        when(opaqueTokens.generate()).thenReturn("the-secret");
        when(opaqueTokens.hash("the-secret")).thenReturn("the-hash");
    }

    @Test
    void opening_a_session_stores_it_with_its_maximum_lifetime() {
        when(sessions.saveSession(any(Session.class))).thenAnswer(i -> i.getArguments()[0]);
        when(accessTokens.issueFor(any(), any())).thenReturn(new AccessToken("jwt", NOW.plusSeconds(900)));

        AccountSession result = service.openSession(account);

        ArgumentCaptor<Session> saved = ArgumentCaptor.forClass(Session.class);
        verify(sessions).saveSession(saved.capture());
        Assertions.assertEquals(account.getId(), saved.getValue().getAccountId());
        Assertions.assertEquals(NOW.plus(Duration.ofDays(30)), saved.getValue().getExpiresAt());
        verify(accessTokens).issueFor(account, saved.getValue().getId());
        Assertions.assertEquals("jwt", result.accessToken().value());
    }

    @Test
    void only_the_hash_of_the_refresh_token_is_stored() {
        Session session = Session.builder().accountId(account.getId()).expiresAt(NOW.plus(Duration.ofDays(30))).build();
        when(accessTokens.issueFor(any(), any())).thenReturn(new AccessToken("jwt", NOW.plusSeconds(900)));

        AccountSession result = service.issueTokens(account, session);

        ArgumentCaptor<RefreshToken> stored = ArgumentCaptor.forClass(RefreshToken.class);
        verify(sessions).saveRefreshToken(stored.capture());
        Assertions.assertEquals("the-hash", stored.getValue().getTokenHash());
        Assertions.assertEquals(session.getId(), stored.getValue().getSessionId());
        Assertions.assertEquals("the-secret", result.refreshToken().value());
        Assertions.assertEquals(NOW.plus(Duration.ofDays(7)), result.refreshToken().expiresAt());
    }

    @Test
    void a_refresh_token_never_outlives_its_session() {
        Instant sessionEnd = NOW.plus(Duration.ofDays(2));
        Session session = Session.builder().accountId(account.getId()).expiresAt(sessionEnd).build();
        when(accessTokens.issueFor(any(), any())).thenReturn(new AccessToken("jwt", NOW.plusSeconds(900)));

        AccountSession result = service.issueTokens(account, session);

        Assertions.assertEquals(sessionEnd, result.refreshToken().expiresAt());
    }
}
