package fr.barlords.dndgateapi.domain.functional.model.plan;

import java.util.Map;
import java.util.Optional;

public record Plan(
        String id,
        boolean highlighted,
        Map<BillingInterval, PlanPrice> prices,
        Map<PlanLimit, Integer> limits,
        Map<Feature, Boolean> features,
        PlanBadge badge) {

    public Plan {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A plan needs an id");
        }
        prices = Map.copyOf(prices);
        limits = Map.copyOf(limits);
        features = Map.copyOf(features);
        for (PlanLimit limit : PlanLimit.values()) {
            Integer value = limits.get(limit);
            if (value == null) {
                throw new IllegalArgumentException("Plan " + id + " lacks the limit " + limit.key());
            }
            if (value < 1) {
                throw new IllegalArgumentException("Plan " + id + " has a limit below 1: " + limit.key());
            }
        }
        for (Feature feature : Feature.values()) {
            if (!features.containsKey(feature)) {
                throw new IllegalArgumentException("Plan " + id + " lacks the feature " + feature.key());
            }
        }
    }

    public int limit(PlanLimit limit) {
        return limits.get(limit);
    }

    public boolean has(Feature feature) {
        return features.get(feature);
    }

    public boolean isFree() {
        return prices.isEmpty();
    }

    public Optional<PlanBadge> badgeIfAny() {
        return Optional.ofNullable(badge);
    }
}
