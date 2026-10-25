package fr.barlords.dndgateapi.server.repository;

import fr.barlords.dndgateapi.domain.functional.model.plan.BillingInterval;
import fr.barlords.dndgateapi.domain.functional.model.plan.SubscriptionStatus;
import fr.barlords.dndgateapi.server.entity.SubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

    Optional<SubscriptionEntity> findByStripeCustomerId(String stripeCustomerId);

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

    /**
     * One statement, a compare-and-set on {@code updated_at}: 1 row written means the row was still the one read,
     * 0 that it changed or no longer exists. An update never creates a row.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SubscriptionEntity s set s.stripeSubscriptionId = :stripeSubscriptionId, s.planId = :planId, "
            + "s.billingInterval = :billingInterval, s.status = :status, s.currentPeriodEnd = :currentPeriodEnd, "
            + "s.cancelAtPeriodEnd = :cancelAtPeriodEnd, s.updatedAt = :updatedAt "
            + "where s.accountId = :accountId and s.updatedAt = :readUpdatedAt")
    int updateIfUnchanged(@Param("accountId") UUID accountId,
                          @Param("stripeSubscriptionId") String stripeSubscriptionId,
                          @Param("planId") String planId,
                          @Param("billingInterval") BillingInterval billingInterval,
                          @Param("status") SubscriptionStatus status,
                          @Param("currentPeriodEnd") Instant currentPeriodEnd,
                          @Param("cancelAtPeriodEnd") boolean cancelAtPeriodEnd,
                          @Param("updatedAt") Instant updatedAt,
                          @Param("readUpdatedAt") Instant readUpdatedAt);
}
