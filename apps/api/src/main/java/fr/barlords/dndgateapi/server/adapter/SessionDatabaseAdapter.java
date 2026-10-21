package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.model.account.RefreshToken;
import fr.barlords.dndgateapi.domain.functional.model.account.Session;
import fr.barlords.dndgateapi.domain.port.out.SessionPersistenceSpi;
import fr.barlords.dndgateapi.server.entity.RefreshTokenEntity;
import fr.barlords.dndgateapi.server.entity.SessionEntity;
import fr.barlords.dndgateapi.server.repository.RefreshTokenRepository;
import fr.barlords.dndgateapi.server.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionDatabaseAdapter implements SessionPersistenceSpi {

    private final SessionRepository sessionRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public Session saveSession(Session session) {
        return toDomain(sessionRepository.save(toEntity(session)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Session> findSessionById(UUID id) {
        return sessionRepository.findById(id).map(SessionDatabaseAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSessionActive(UUID id, Instant now) {
        return sessionRepository.isActive(id, now);
    }

    @Override
    @Transactional
    public void revokeSession(UUID id, Instant at) {
        sessionRepository.revoke(id, at);
    }

    @Override
    @Transactional
    public void revokeAllSessionsOfAccount(UUID accountId, Instant at) {
        sessionRepository.revokeAllOfAccount(accountId, at);
    }

    @Override
    @Transactional
    public void saveRefreshToken(RefreshToken refreshToken) {
        refreshTokenRepository.save(RefreshTokenEntity.builder()
                .id(refreshToken.getId())
                .sessionId(refreshToken.getSessionId())
                .tokenHash(refreshToken.getTokenHash())
                .expiresAt(refreshToken.getExpiresAt())
                .usedAt(refreshToken.getUsedAt())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findRefreshTokenByHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash)
                .map(entity -> RefreshToken.builder()
                        .id(entity.getId())
                        .sessionId(entity.getSessionId())
                        .tokenHash(entity.getTokenHash())
                        .expiresAt(entity.getExpiresAt())
                        .usedAt(entity.getUsedAt())
                        .build());
    }

    @Override
    @Transactional
    public boolean markRefreshTokenUsed(UUID id, Instant at) {
        return refreshTokenRepository.markUsed(id, at) == 1;
    }

    @Override
    @Transactional
    public void deleteExpiredSessions(Instant before) {
        sessionRepository.deleteExpired(before);
    }

    private static SessionEntity toEntity(Session session) {
        return SessionEntity.builder()
                .id(session.getId())
                .accountId(session.getAccountId())
                .createdAt(session.getCreatedAt())
                .expiresAt(session.getExpiresAt())
                .revokedAt(session.getRevokedAt())
                .build();
    }

    private static Session toDomain(SessionEntity entity) {
        return Session.builder()
                .id(entity.getId())
                .accountId(entity.getAccountId())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .revokedAt(entity.getRevokedAt())
                .build();
    }

}
