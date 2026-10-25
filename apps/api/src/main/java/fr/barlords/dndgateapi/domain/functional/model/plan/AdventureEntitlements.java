package fr.barlords.dndgateapi.domain.functional.model.plan;

import java.util.Map;
import java.util.Set;

/** What works for an account in an adventure, and how much of each adventure limit is used. */
public record AdventureEntitlements(Set<Feature> features, Map<PlanLimit, LimitUsage> limits) {
}
