package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.plan.Subscription;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The subscription row of an account. It is only ever written by {@link #createCustomerIfAbsent} (the first checkout)
 * and by the compare-and-set {@link #updateIfUnchanged} (the sync): no write that could overwrite a fresher state.
 */
public interface SubscriptionPersistenceSpi {

    Optional<Subscription> findByAccountId(UUID accountId);

    Optional<Subscription> findByStripeCustomerId(String stripeCustomerId);

    /**
     * Records the customer of an account that has no row yet (no subscription known), in one statement, and returns
     * the row the account has afterwards: the new one, or the one a concurrent call recorded first, left as it is.
     */
    Subscription createCustomerIfAbsent(UUID accountId, String stripeCustomerId, Instant now);

    /**
     * Writes {@code next} over the row of its account only if that row still has the {@code updated_at} it was read
     * with. Never creates a row.
     *
     * @return false when the row changed or disappeared since it was read
     */
    boolean updateIfUnchanged(Subscription next, Instant readUpdatedAt);

    void deleteByAccountId(UUID accountId);
}
