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

    static SubscriptionEntity fromDomain(Subscription domain) {
        return SubscriptionEntity.builder()
                .accountId(domain.getAccountId())
                .stripeCustomerId(domain.getStripeCustomerId())
                .stripeSubscriptionId(domain.getStripeSubscriptionId())
                .planId(domain.getPlanId())
                .billingInterval(domain.getInterval())
                .status(domain.getStatus())
                .currentPeriodEnd(domain.getCurrentPeriodEnd())
                .cancelAtPeriodEnd(domain.isCancelAtPeriodEnd())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
