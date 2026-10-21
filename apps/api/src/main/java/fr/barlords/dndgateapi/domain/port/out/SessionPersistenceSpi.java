package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.account.RefreshToken;
import fr.barlords.dndgateapi.domain.functional.model.account.Session;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionPersistenceSpi {

    Session saveSession(Session session);

    Optional<Session> findSessionById(UUID id);

    boolean isSessionActive(UUID id, Instant now);

    void revokeSession(UUID id, Instant at);

    void revokeAllSessionsOfAccount(UUID accountId, Instant at);

    void saveRefreshToken(RefreshToken refreshToken);

    Optional<RefreshToken> findRefreshTokenByHash(String tokenHash);

    /**
     * Marks the refresh token as used, atomically.
     *
     * @return false if it had already been used
     */
    boolean markRefreshTokenUsed(UUID id, Instant at);

    /** Removes the sessions expired before the given instant, with their refresh tokens. */
    void deleteExpiredSessions(Instant before);

}
