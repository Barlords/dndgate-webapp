package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.account.PendingSignup;

import java.time.Instant;

/** The signups that wait for the confirmation of their address. */
public interface PendingSignupPersistenceSpi {

    /**
     * Atomically records the signup of its address, replacing the one that is there unless that one was created after
     * {@code recentSince}: true when the signup was recorded, false when a recent one was kept. Two simultaneous calls
     * for the same address cannot both get true, and neither fails.
     */
    boolean saveUnlessRecent(PendingSignup signup, Instant recentSince);

    /** Forgets the signups that expired before the given instant. */
    void deleteExpiredBefore(Instant now);

}
