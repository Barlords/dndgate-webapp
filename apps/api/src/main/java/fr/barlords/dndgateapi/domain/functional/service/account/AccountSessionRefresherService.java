package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.account.InvalidRefreshTokenException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.AccountSession;
import fr.barlords.dndgateapi.domain.functional.model.account.RefreshToken;
import fr.barlords.dndgateapi.domain.functional.model.account.Session;
import fr.barlords.dndgateapi.domain.port.in.account.AccountSessionRefresherApi;
import fr.barlords.dndgateapi.domain.port.out.AccessRevocationSpi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.OpaqueTokenSpi;
import fr.barlords.dndgateapi.domain.port.out.SessionPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public class AccountSessionRefresherService implements AccountSessionRefresherApi {

    private final AccountPersistenceSpi accounts;

    private final SessionPersistenceSpi sessions;

    private final OpaqueTokenSpi opaqueTokens;

    private final SessionIssuerService sessionIssuer;

    private final Clock clock;

    private final AccessRevocationSpi accessRevocation;

    @Override
    public AccountSession refresh(String refreshToken) {
        Instant now = clock.instant();

        RefreshToken stored = sessions.findRefreshTokenByHash(opaqueTokens.hash(refreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        Session session = sessions.findSessionById(stored.getSessionId())
                .filter(found -> found.isActiveAt(now))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (stored.isUsed()) {
            return revokeBecauseOfReuse(session, now);
        }

        if (stored.isExpiredAt(now)) {
            throw new InvalidRefreshTokenException();
        }

        Account account = accounts.findById(session.getAccountId())
                .filter(Account::isActivated)
                .orElseThrow(InvalidRefreshTokenException::new);

        // Single use: if somebody else consumed it in the meantime, it is a replay as well
        if (!sessions.markRefreshTokenUsed(stored.getId(), now)) {
            return revokeBecauseOfReuse(session, now);
        }

        return sessionIssuer.issueTokens(account, session);
    }

    private AccountSession revokeBecauseOfReuse(Session session, Instant now) {
        log.warn("Refresh token reuse detected, revoking session {} of account {}", session.getId(), session.getAccountId());
        sessions.revokeSession(session.getId(), now);
        // whoever holds the stolen tokens may already listen to the chat
        accessRevocation.sessionRevoked(session.getId());
        throw new InvalidRefreshTokenException();
    }

}
