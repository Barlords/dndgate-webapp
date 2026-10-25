package fr.barlords.dndgateapi.domain.functional.model.plan;

import java.util.Map;

/**
 * What an account sees of its subscription.
 *
 * @param plan           the effective plan
 * @param subscription   the subscription row, null when the account never went to the checkout
 * @param usage          the account limits of the effective plan and how much of each is used
 * @param billingEnabled whether payments can be made at all
 */
public record SubscriptionOverview(Plan plan, Subscription subscription, Map<PlanLimit, LimitUsage> usage,
                                   boolean billingEnabled) {

    public SubscriptionOverview {
        usage = Map.copyOf(usage);
    }
}
