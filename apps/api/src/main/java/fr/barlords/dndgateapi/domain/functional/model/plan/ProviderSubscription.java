package fr.barlords.dndgateapi.domain.functional.model.plan;

import java.time.Instant;

/**
 * A subscription as the payment provider describes it.
 *
 * @param planId product metadata {@code plan_id}, null when the product does not carry it
 */
public record ProviderSubscription(
        String id,
        String planId,
        BillingInterval interval,
        SubscriptionStatus status,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd) {
}
