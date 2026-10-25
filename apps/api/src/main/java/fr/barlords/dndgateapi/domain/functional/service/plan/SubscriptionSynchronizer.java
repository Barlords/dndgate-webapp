package fr.barlords.dndgateapi.domain.functional.service.plan;

import fr.barlords.dndgateapi.domain.functional.model.plan.PlanCatalog;
import fr.barlords.dndgateapi.domain.functional.model.plan.ProviderSubscription;
import fr.barlords.dndgateapi.domain.functional.model.plan.Subscription;
import fr.barlords.dndgateapi.domain.port.out.PaymentProviderSpi;
import fr.barlords.dndgateapi.domain.port.out.SubscriptionPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Records in the subscription row of an account what the payment provider says now. Every notification leads to
 * the same reading of all the subscriptions of the customer, so the order and the repetition of notifications do
 * not matter.
 */
@Slf4j
@RequiredArgsConstructor
public class SubscriptionSynchronizer {

    private static final Comparator<ProviderSubscription> BY_PERIOD_END = Comparator.comparing(
            ProviderSubscription::currentPeriodEnd, Comparator.nullsFirst(Comparator.<Instant>naturalOrder()));

    private final PaymentProviderSpi provider;
    private final SubscriptionPersistenceSpi subscriptions;
    private final PlanCatalog catalog;
    private final Clock clock;

    /**
     * Keeps the best subscription that grants a plan (highest plan, then latest period end); when none does, the
     * one whose period ends last; when the customer has none at all, the row as it is.
     */
    public void sync(Subscription row) {
        String customerId = row.getStripeCustomerId();
        List<ProviderSubscription> all = provider.findSubscriptions(customerId);
        List<ProviderSubscription> granting = all.stream().filter(found -> found.status().grantsPlan()).toList();
        if (granting.size() > 1) {
            log.error("The Stripe customer {} has {} subscriptions that grant a plan ({}): the best one is kept, "
                    + "refund the others by hand", customerId, granting.size(),
                    granting.stream().map(ProviderSubscription::id).toList());
        }
        Optional<ProviderSubscription> chosen = granting.isEmpty()
                ? all.stream().max(BY_PERIOD_END)
                : granting.stream().max(Comparator.comparingInt(this::rank).thenComparing(BY_PERIOD_END));
        if (chosen.isEmpty()) {
            return;
        }
        ProviderSubscription current = chosen.get();
        // an unknown plan id is kept as read: the default plan applies to it (EntitlementApi.planOf)
        Subscription updated = row
                .withStripeSubscriptionId(current.id())
                .withPlanId(current.planId())
                .withInterval(current.interval())
                .withStatus(current.status())
                .withCurrentPeriodEnd(current.currentPeriodEnd())
                .withCancelAtPeriodEnd(current.cancelAtPeriodEnd());
        if (!updated.equals(row)) {
            subscriptions.save(updated.withUpdatedAt(clock.instant()));
        }
    }

    /** Unknown plans rank below every plan of the catalog. */
    private int rank(ProviderSubscription subscription) {
        return catalog.find(subscription.planId()).map(catalog::rank).orElse(-1);
    }
}
