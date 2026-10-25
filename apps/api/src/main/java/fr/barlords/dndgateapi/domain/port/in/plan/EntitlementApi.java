package fr.barlords.dndgateapi.domain.port.in.plan;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.plan.Feature;
import fr.barlords.dndgateapi.domain.functional.model.plan.Plan;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanBadge;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** What the plan of an account, or of the master of an adventure, allows. */
public interface EntitlementApi {

    /** The plan of the subscription when it applies, the default plan otherwise. */
    Plan planOf(UUID accountId);

    /** The plan of the game master, the default plan while the seat is vacant. */
    Plan masterPlanOf(Adventure adventure);

    /** Refuses when {@code used} has reached the limit of the plan of the account. */
    void verifyAccountLimit(UUID accountId, PlanLimit limit, long used);

    /** Refuses when {@code used} has reached the limit of the plan of the game master. */
    void verifyAdventureLimit(Adventure adventure, PlanLimit limit, long used);

    /** The adventure may be null outside an adventure. */
    boolean allows(UUID accountId, Adventure adventureOrNull, Feature feature);

    void verifyFeature(UUID accountId, Adventure adventureOrNull, Feature feature);

    /** The shareable and master features that work for the account in the adventure. */
    Set<Feature> availableFeatures(UUID accountId, Adventure adventure);

    Optional<PlanBadge> badgeOf(UUID accountId);
}
