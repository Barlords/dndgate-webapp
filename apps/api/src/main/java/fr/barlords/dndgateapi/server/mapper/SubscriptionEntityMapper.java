package fr.barlords.dndgateapi.server.mapper;

import fr.barlords.dndgateapi.domain.functional.model.plan.Subscription;
import fr.barlords.dndgateapi.server.entity.SubscriptionEntity;

public interface SubscriptionEntityMapper {

    static Subscription toDomain(SubscriptionEntity entity) {
        return Subscription.builder()
                .accountId(entity.getAccountId())
                .stripeCustomerId(entity.getStripeCustomerId())
                .stripeSubscriptionId(entity.getStripeSubscriptionId())
                .planId(entity.getPlanId())
                .interval(entity.getBillingInterval())
                .status(entity.getStatus())
                .currentPeriodEnd(entity.getCurrentPeriodEnd())
                .cancelAtPeriodEnd(entity.isCancelAtPeriodEnd())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
