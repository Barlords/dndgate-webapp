package fr.barlords.dndgateapi.server.repository;

import fr.barlords.dndgateapi.server.entity.SubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

    Optional<SubscriptionEntity> findByStripeCustomerId(String stripeCustomerId);

    List<SubscriptionEntity> findByAccountIdIn(Collection<UUID> accountIds);
}
