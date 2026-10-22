package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.account.PendingSignup;

import java.time.Instant;
import java.util.Optional;

/** The signups that wait for the confirmation of their address. */
public interface PendingSignupPersistenceSpi {

    /**
     * Atomically records the signup of its address, replacing the one that is there unless that one was created after
     * {@code recentSince}: true when the signup was recorded, false when a recent one was kept. Two simultaneous calls
     * for the same address cannot both get true, and neither fails.
     */
    boolean saveUnlessRecent(PendingSignup signup, Instant recentSince);

    /** The signup of the (normalised) address, if any. */
    Optional<PendingSignup> findByEmail(String email);

    /**
     * Atomically gives the signup a new code, creation date and expiry, unless it was created after
     * {@code recentSince}: true when it was renewed, false when there is no signup for the address or a recent one
     * was kept. Two simultaneous calls cannot both get true.
     */
    boolean renewUnlessRecent(String email, String codeHash, Instant createdAt, Instant expiresAt, Instant recentSince);

    /**
     * Forgets the signup of the address if it still holds that code hash: true when a row was removed. Removing it is
     * what uses a signup up, so of several simultaneous confirmations only one gets true; and a signup replaced
     * by a newer one in the meantime (another code) is left alone.
     */
    boolean deleteByEmailAndCodeHash(String email, String codeHash);

    /** Forgets the signups that expired before the given instant. */
    void deleteExpiredBefore(Instant now);

}
