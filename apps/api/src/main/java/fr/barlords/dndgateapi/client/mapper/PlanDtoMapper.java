package fr.barlords.dndgateapi.client.mapper;

import fr.barlords.dndgateapi.client.dto.plan.LimitUsageDto;
import fr.barlords.dndgateapi.client.dto.plan.PlanCatalogDto;
import fr.barlords.dndgateapi.client.dto.plan.PlanDto;
import fr.barlords.dndgateapi.client.dto.plan.PlanPriceDto;
import fr.barlords.dndgateapi.client.dto.plan.SubscriptionDto;
import fr.barlords.dndgateapi.domain.functional.model.plan.BillingInterval;
import fr.barlords.dndgateapi.domain.functional.model.plan.Feature;
import fr.barlords.dndgateapi.domain.functional.model.plan.Plan;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanBadge;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanCatalog;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import fr.barlords.dndgateapi.domain.functional.model.plan.Subscription;
import fr.barlords.dndgateapi.domain.functional.model.plan.SubscriptionOverview;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** The keys are the ones of plans.json; the Stripe lookup keys stay on the server. */
public interface PlanDtoMapper {

    static PlanCatalogDto toDto(PlanCatalog catalog) {
        return new PlanCatalogDto(catalog.currency(), catalog.defaultPlanId(),
                catalog.plans().stream().map(PlanDtoMapper::toDto).toList());
    }

    static PlanDto toDto(Plan plan) {
        Map<String, PlanPriceDto> prices = new LinkedHashMap<>();
        for (BillingInterval interval : BillingInterval.values()) {
            if (plan.prices().containsKey(interval)) {
                prices.put(interval.key(), new PlanPriceDto(plan.prices().get(interval).amount()));
            }
        }
        Map<String, Integer> limits = new LinkedHashMap<>();
        for (PlanLimit limit : PlanLimit.values()) {
            limits.put(limit.key(), plan.limit(limit));
        }
        Map<String, Boolean> features = new LinkedHashMap<>();
        for (Feature feature : Feature.values()) {
            features.put(feature.key(), plan.has(feature));
        }
        return new PlanDto(plan.id(), plan.highlighted(), prices, limits, features,
                plan.badgeIfAny().map(PlanBadge::key).orElse(null));
    }

    static SubscriptionDto toDto(SubscriptionOverview overview) {
        Map<String, LimitUsageDto> usage = new LinkedHashMap<>();
        for (PlanLimit limit : PlanLimit.values()) {
            if (overview.usage().containsKey(limit)) {
                usage.put(limit.key(), new LimitUsageDto(overview.usage().get(limit).used(), overview.usage().get(limit).max()));
            }
        }
        Subscription subscription = overview.subscription();
        if (subscription == null) {
            return new SubscriptionDto(overview.plan().id(), null, null, null, false, overview.billingEnabled(), usage);
        }
        return new SubscriptionDto(
                overview.plan().id(),
                subscription.getInterval() == null ? null : subscription.getInterval().key(),
                subscription.getStatus() == null ? null : subscription.getStatus().name().toLowerCase(Locale.ROOT),
                subscription.getCurrentPeriodEnd(),
                subscription.isCancelAtPeriodEnd(),
                overview.billingEnabled(),
                usage);
    }
}
