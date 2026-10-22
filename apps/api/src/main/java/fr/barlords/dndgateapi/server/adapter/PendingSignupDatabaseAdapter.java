package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.model.account.PendingSignup;
import fr.barlords.dndgateapi.domain.port.out.PendingSignupPersistenceSpi;
import fr.barlords.dndgateapi.server.entity.PendingSignupEntity;
import fr.barlords.dndgateapi.server.repository.PendingSignupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PendingSignupDatabaseAdapter implements PendingSignupPersistenceSpi {

    private final PendingSignupRepository repository;

    @Override
    @Transactional
    public boolean saveUnlessRecent(PendingSignup signup, Instant recentSince) {
        return repository.saveUnlessRecent(signup.getId(), signup.getEmail(), signup.getPseudo(), signup.getPasswordHash(),
                signup.getCodeHash(), signup.getCreatedAt(), signup.getExpiresAt(), recentSince) == 1;
    }

    @Override
    @Transactional
    public Optional<PendingSignup> findByEmail(String email) {
        return repository.findByEmail(email).map(PendingSignupDatabaseAdapter::toDomain);
    }

    @Override
    @Transactional
    public boolean renewUnlessRecent(String email, String codeHash, Instant createdAt, Instant expiresAt, Instant recentSince) {
        return repository.renewUnlessRecent(email, codeHash, createdAt, expiresAt, recentSince) == 1;
    }

    @Override
    @Transactional
    public boolean deleteByEmailAndCodeHash(String email, String codeHash) {
        return repository.deleteByEmailAndCodeHash(email, codeHash) == 1;
    }

    private static PendingSignup toDomain(PendingSignupEntity entity) {
        return PendingSignup.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .pseudo(entity.getPseudo())
                .passwordHash(entity.getPasswordHash())
                .codeHash(entity.getCodeHash())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();
    }

    @Override
    @Transactional
    public void deleteExpiredBefore(Instant now) {
        repository.deleteExpiredBefore(now);
    }

}
