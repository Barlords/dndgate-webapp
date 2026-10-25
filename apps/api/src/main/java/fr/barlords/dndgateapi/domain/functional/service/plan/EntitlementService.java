package fr.barlords.dndgateapi.domain.functional.service.plan;

import fr.barlords.dndgateapi.domain.functional.exception.plan.FeatureNotIncludedException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.PlanLimitReachedException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.plan.Feature;
import fr.barlords.dndgateapi.domain.functional.model.plan.Plan;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanBadge;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanCatalog;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanDecider;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import fr.barlords.dndgateapi.domain.functional.model.plan.Subscription;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.port.out.SubscriptionPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class EntitlementService implements EntitlementApi {

    private final SubscriptionPersistenceSpi subscriptions;
    private final PlanCatalog catalog;

    @Override
    public Plan planOf(UUID accountId) {
        Optional<Subscription> subscription = subscriptions.findByAccountId(accountId)
                .filter(found -> found.getStatus() != null && found.getStatus().grantsPlan());
        if (subscription.isEmpty()) {
            return catalog.defaultPlan();
        }
        String planId = subscription.get().getPlanId();
        Optional<Plan> plan = catalog.find(planId);
        if (plan.isEmpty()) {
            log.error("Account {} has a subscription to the unknown plan {}: the default plan applies", accountId, planId);
            return catalog.defaultPlan();
        }
        return plan.get();
    }

    @Override
    public Plan masterPlanOf(Adventure adventure) {
        if (adventure.isMasterVacant()) {
            return catalog.defaultPlan();
        }
        return planOf(adventure.getOwnerId());
    }

    @Override
    public void verifyAccountLimit(UUID accountId, PlanLimit limit, long used) {
        verifyLimit(planOf(accountId), limit, used, PlanDecider.ACCOUNT);
    }

    @Override
    public void verifyAdventureLimit(Adventure adventure, PlanLimit limit, long used) {
        verifyLimit(masterPlanOf(adventure), limit, used, PlanDecider.MASTER);
    }

    @Override
    public boolean allows(UUID accountId, Adventure adventureOrNull, Feature feature) {
        return switch (feature.reach()) {
            case ACCOUNT -> planOf(accountId).has(feature);
            case MASTER -> adventureOrNull != null && masterPlanOf(adventureOrNull).has(feature);
            case SHAREABLE -> planOf(accountId).has(feature)
                    || (adventureOrNull != null && sharedByMaster(adventureOrNull, feature));
        };
    }

    @Override
    public void verifyFeature(UUID accountId, Adventure adventureOrNull, Feature feature) {
        if (!allows(accountId, adventureOrNull, feature)) {
            // A master feature is unlocked by the plan of the game master, not by the one of the caller
            PlanDecider decidedBy = feature.reach() == Feature.Reach.MASTER && adventureOrNull != null
                    ? PlanDecider.MASTER
                    : PlanDecider.ACCOUNT;
            Plan plan = decidedBy == PlanDecider.MASTER ? masterPlanOf(adventureOrNull) : planOf(accountId);
            String upgrade = catalog.lowestWith(plan, feature).map(Plan::id).orElse(null);
            throw new FeatureNotIncludedException(feature, plan.id(), upgrade, decidedBy);
        }
    }

    @Override
    public Set<Feature> availableFeatures(UUID accountId, Adventure adventure) {
        Set<Feature> available = EnumSet.noneOf(Feature.class);
        for (Feature feature : Feature.values()) {
            if (feature.reach() != Feature.Reach.ACCOUNT && allows(accountId, adventure, feature)) {
                available.add(feature);
            }
        }
        return available;
    }

    @Override
    public Optional<PlanBadge> badgeOf(UUID accountId) {
        return planOf(accountId).badgeIfAny();
    }

    private boolean sharedByMaster(Adventure adventure, Feature feature) {
        Plan master = masterPlanOf(adventure);
        return master.has(Feature.EQUIPPED_TABLE) && master.has(feature);
    }

    private void verifyLimit(Plan plan, PlanLimit limit, long used, PlanDecider decidedBy) {
        int max = plan.limit(limit);
        if (used >= max) {
            String upgrade = catalog.lowestAbove(plan, limit).map(Plan::id).orElse(null);
            throw new PlanLimitReachedException(limit, max, plan.id(), upgrade, decidedBy);
        }
    }
}
