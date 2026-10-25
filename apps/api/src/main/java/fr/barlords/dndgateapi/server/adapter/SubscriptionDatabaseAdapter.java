package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.model.plan.Subscription;
import fr.barlords.dndgateapi.domain.port.out.SubscriptionPersistenceSpi;
import fr.barlords.dndgateapi.server.mapper.SubscriptionEntityMapper;
import fr.barlords.dndgateapi.server.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionDatabaseAdapter implements SubscriptionPersistenceSpi {

    private final SubscriptionRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Subscription> findByAccountId(UUID accountId) {
        return repository.findById(accountId).map(SubscriptionEntityMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Subscription> findByStripeCustomerId(String stripeCustomerId) {
        return repository.findByStripeCustomerId(stripeCustomerId).map(SubscriptionEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public Subscription createCustomerIfAbsent(UUID accountId, String stripeCustomerId, Instant now) {
        repository.insertCustomerIfAbsent(accountId, stripeCustomerId, now);
        return repository.findById(accountId).map(SubscriptionEntityMapper::toDomain).orElseThrow();
    }

    @Override
    @Transactional
    public boolean updateIfUnchanged(Subscription next, Instant readUpdatedAt) {
        return repository.updateIfUnchanged(next.getAccountId(), next.getStripeSubscriptionId(), next.getPlanId(),
                next.getInterval(), next.getStatus(), next.getCurrentPeriodEnd(), next.isCancelAtPeriodEnd(),
                next.getUpdatedAt(), readUpdatedAt) == 1;
    }

    @Override
    @Transactional
    public void deleteByAccountId(UUID accountId) {
        repository.deleteById(accountId);
    }
}
