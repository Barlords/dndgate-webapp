package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;
import fr.barlords.dndgateapi.domain.functional.model.account.LoginFailures;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Remembers the wrong passwords, per account and per client, for the lockout. */
public interface LoginFailureSpi {

    Optional<LoginFailures> find(UUID accountId, ClientAddress client);

    /**
     * Counts one more failure of this client against this account, atomically, and locks the client out of the
     * account until the given instant when the number of consecutive failures reaches the maximum.
     * A lock that has expired restarts the count from one.
     */
    void register(UUID accountId, ClientAddress client, int maxAttempts, Instant lockedUntil, Instant now);

    /** Forgets the failures of this client against this account (it just gave the right password). */
    void clear(UUID accountId, ClientAddress client);

    /** How many different clients failed against this account since the given instant. */
    int countClientsFailingSince(UUID accountId, Instant since);

    /** Forgets every failure recorded against this account (it is being deleted). */
    void deleteByAccount(UUID accountId);

    /** Forgets the failures older than this instant. */
    void deleteFailedBefore(Instant before);

}
