package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.plan.Subscription;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPersistenceSpi {

    Optional<Subscription> findByAccountId(UUID accountId);

    Optional<Subscription> findByStripeCustomerId(String stripeCustomerId);

    Map<UUID, Subscription> findByAccountIds(Collection<UUID> accountIds);

    Subscription save(Subscription subscription);

    void deleteByAccountId(UUID accountId);
}
