package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.model.account.AccessToken;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.AccountSession;
import fr.barlords.dndgateapi.domain.functional.model.account.IssuedRefreshToken;
import fr.barlords.dndgateapi.domain.functional.model.account.RefreshToken;
import fr.barlords.dndgateapi.domain.functional.model.account.Session;
import fr.barlords.dndgateapi.domain.port.out.AccessTokenSpi;
import fr.barlords.dndgateapi.domain.port.out.OpaqueTokenSpi;
import fr.barlords.dndgateapi.domain.port.out.SessionPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Opens sessions and issues the tokens that go with them.
 */
@RequiredArgsConstructor
public class SessionIssuerService {

    private final SessionPersistenceSpi sessions;

    private final AccessTokenSpi accessTokens;

    private final OpaqueTokenSpi opaqueTokens;

    private final Clock clock;

    /** Validity of one refresh token. */
    private final Duration refreshTokenTtl;

    /** Absolute lifetime of a session, whatever the number of refreshes. */
    private final Duration sessionMaxLifetime;

    public AccountSession openSession(Account account) {
        Instant now = clock.instant();

        Session session = sessions.saveSession(Session.builder()
                .accountId(account.getId())
                .createdAt(now)
                .expiresAt(now.plus(sessionMaxLifetime))
                .build());

        return issueTokens(account, session);
    }

    public AccountSession issueTokens(Account account, Session session) {
        Instant now = clock.instant();
        Instant refreshExpiresAt = now.plus(refreshTokenTtl);
        if (refreshExpiresAt.isAfter(session.getExpiresAt())) {
            refreshExpiresAt = session.getExpiresAt();
        }

        String secret = opaqueTokens.generate();
        sessions.saveRefreshToken(RefreshToken.builder()
                .sessionId(session.getId())
                .tokenHash(opaqueTokens.hash(secret))
                .expiresAt(refreshExpiresAt)
                .build());

        AccessToken accessToken = accessTokens.issueFor(account, session.getId());

        return new AccountSession(account, accessToken, new IssuedRefreshToken(secret, refreshExpiresAt));
    }

}
