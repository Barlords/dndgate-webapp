package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.functional.model.plan.Subscription;
import fr.barlords.dndgateapi.domain.port.out.SubscriptionPersistenceSpi;
import fr.barlords.dndgateapi.server.entity.SubscriptionEntity;
import fr.barlords.dndgateapi.server.mapper.SubscriptionEntityMapper;
import fr.barlords.dndgateapi.server.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    @Transactional(readOnly = true)
    public Map<UUID, Subscription> findByAccountIds(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByAccountIdIn(accountIds).stream()
                .map(SubscriptionEntityMapper::toDomain)
                .collect(Collectors.toMap(Subscription::getAccountId, Function.identity()));
    }

    @Override
    @Transactional
    public Subscription save(Subscription subscription) {
        SubscriptionEntity saved = repository.saveAndFlush(SubscriptionEntityMapper.fromDomain(subscription));
        return SubscriptionEntityMapper.toDomain(saved);
    }

    @Override
    @Transactional
    public Subscription createCustomerIfAbsent(UUID accountId, String stripeCustomerId, Instant now) {
        repository.insertCustomerIfAbsent(accountId, stripeCustomerId, now);
        return repository.findById(accountId).map(SubscriptionEntityMapper::toDomain).orElseThrow();
    }

    @Override
    @Transactional
    public void deleteByAccountId(UUID accountId) {
        repository.deleteById(accountId);
    }
}
