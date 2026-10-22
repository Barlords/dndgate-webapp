package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.model.account.PendingSignup;
import fr.barlords.dndgateapi.domain.port.out.PendingSignupPersistenceSpi;
import fr.barlords.dndgateapi.server.repository.PendingSignupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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
    public void deleteExpiredBefore(Instant now) {
        repository.deleteExpiredBefore(now);
    }

}
