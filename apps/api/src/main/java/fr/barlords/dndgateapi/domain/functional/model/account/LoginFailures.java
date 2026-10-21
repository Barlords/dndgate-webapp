package fr.barlords.dndgateapi.domain.functional.model.account;

import java.time.Instant;

/**
 * The failed logins of one account seen from one client.
 *
 * @param attempts    consecutive failures since the last success (or the end of the last lock)
 * @param lockedUntil the client cannot use the account until this instant, null when it is not locked
 */
public record LoginFailures(int attempts, Instant lockedUntil) {

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

}
