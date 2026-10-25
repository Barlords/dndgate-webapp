package fr.barlords.dndgateapi.client.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * The subscription of the caller.
 *
 * @param planId         the plan in force (the default plan unless a subscription grants one)
 * @param interval       null when no subscription is known
 * @param status         the status of the subscription at Stripe, null when no subscription is known
 * @param billingEnabled false when payments cannot be made at all (the routes of /billing answer 503)
 * @param usage          the account limits of the plan in force by their JSON key, and how much of each is used
 */
public record SubscriptionDto(
        String planId,
        @Schema(nullable = true, description = "monthly or yearly") String interval,
        @Schema(nullable = true, description = "active, trialing, past_due (the plan applies), canceled, unpaid, "
                + "incomplete, incomplete_expired or paused (it does not)") String status,
        @Schema(nullable = true) Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        boolean billingEnabled,
        Map<String, LimitUsageDto> usage) {
}
