package fr.barlords.dndgateapi.domain.functional.model.plan;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.Instant;
import java.util.UUID;

/** What the billing provider told us about an account; status is null until a subscription exists. */
@Value
@Builder
@With
public class Subscription {

    UUID accountId;
    String stripeCustomerId;
    String stripeSubscriptionId;
    String planId;
    BillingInterval interval;
    SubscriptionStatus status;
    Instant currentPeriodEnd;
    boolean cancelAtPeriodEnd;
    Instant updatedAt;
}
