package fr.barlords.dndgateapi.server.repository;

import fr.barlords.dndgateapi.server.entity.SubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

    Optional<SubscriptionEntity> findByStripeCustomerId(String stripeCustomerId);

    List<SubscriptionEntity> findByAccountIdIn(Collection<UUID> accountIds);

    /**
     * One statement, so that two simultaneous first checkouts of an account cannot overwrite each other's customer:
     * 1 row written means this customer was recorded, 0 means the account already had a row (kept as it is).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "insert into subscription (account_id, stripe_customer_id, cancel_at_period_end, updated_at) "
            + "values (:accountId, :stripeCustomerId, false, :updatedAt) "
            + "on conflict (account_id) do nothing", nativeQuery = true)
    int insertCustomerIfAbsent(@Param("accountId") UUID accountId, @Param("stripeCustomerId") String stripeCustomerId,
                               @Param("updatedAt") Instant updatedAt);
}
