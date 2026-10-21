package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;
import fr.barlords.dndgateapi.domain.functional.model.account.LoginFailures;
import fr.barlords.dndgateapi.domain.port.out.LoginFailureSpi;
import fr.barlords.dndgateapi.server.repository.LoginFailureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * The failures are written in their own transaction: a wrong password makes the caller's transaction roll back
 * (the deletion of an account, for instance), the failure must be remembered all the same.
 * Only a hash of the client address is stored.
 */
@Service
@RequiredArgsConstructor
public class LoginFailureDatabaseAdapter implements LoginFailureSpi {

    private final LoginFailureRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<LoginFailures> find(UUID accountId, ClientAddress client) {
        return repository.findByAccountIdAndClientHash(accountId, hash(client))
                .map(entity -> new LoginFailures(entity.getFailedAttempts(), entity.getLockedUntil()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void register(UUID accountId, ClientAddress client, int maxAttempts, Instant lockedUntil, Instant now) {
        repository.register(accountId, hash(client), maxAttempts, lockedUntil, now);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clear(UUID accountId, ClientAddress client) {
        repository.deleteByAccountAndClient(accountId, hash(client));
    }

    @Override
    @Transactional(readOnly = true)
    public int countClientsFailingSince(UUID accountId, Instant since) {
        return repository.countClientsFailingSince(accountId, since);
    }

    @Override
    @Transactional
    public void deleteFailedBefore(Instant before) {
        repository.deleteFailedBefore(before);
    }

    private static String hash(ClientAddress client) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(client.value().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

}
