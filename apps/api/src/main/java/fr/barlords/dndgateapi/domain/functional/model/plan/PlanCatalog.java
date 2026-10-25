package fr.barlords.dndgateapi.domain.functional.model.plan;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The offers, from the cheapest to the most complete: the order of the plans is their rank. */
public record PlanCatalog(String currency, String defaultPlanId, List<Plan> plans) {

    public PlanCatalog {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("The catalog needs a currency");
        }
        if (plans == null || plans.isEmpty()) {
            throw new IllegalArgumentException("The catalog needs at least one plan");
        }
        plans = List.copyOf(plans);
        if (plans.stream().noneMatch(plan -> plan.id().equals(defaultPlanId))) {
            throw new IllegalArgumentException("Unknown default plan: " + defaultPlanId);
        }
        Set<String> ids = new HashSet<>();
        for (Plan plan : plans) {
            if (!ids.add(plan.id())) {
                throw new IllegalArgumentException("Duplicated plan id: " + plan.id());
            }
            if (!plan.id().equals(defaultPlanId) && plan.isFree()) {
                throw new IllegalArgumentException("Plan " + plan.id() + " is not the default plan and has no price");
            }
        }
        Plan defaultPlan = plans.stream().filter(plan -> plan.id().equals(defaultPlanId)).findFirst().orElseThrow();
        if (!defaultPlan.isFree()) {
            throw new IllegalArgumentException("The default plan must be free");
        }
    }

    public Plan defaultPlan() {
        return findOrDefault(defaultPlanId);
    }

    public Optional<Plan> find(String id) {
        return plans.stream().filter(plan -> plan.id().equals(id)).findFirst();
    }

    public Plan findOrDefault(String id) {
        return find(id).orElseGet(() -> find(defaultPlanId).orElseThrow());
    }

    public int rank(Plan plan) {
        return plans.indexOf(plan);
    }

    public Optional<Plan> lowestAbove(Plan current, PlanLimit limit) {
        return plans.stream().skip(rank(current) + 1L).filter(plan -> plan.limit(limit) > current.limit(limit))
                .findFirst();
    }

    public Optional<Plan> lowestWith(Plan current, Feature feature) {
        return plans.stream().skip(rank(current) + 1L).filter(plan -> plan.has(feature)).findFirst();
    }
}
