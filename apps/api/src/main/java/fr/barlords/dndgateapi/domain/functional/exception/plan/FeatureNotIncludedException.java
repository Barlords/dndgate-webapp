package fr.barlords.dndgateapi.domain.functional.exception.plan;

import fr.barlords.dndgateapi.domain.functional.model.plan.Feature;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanDecider;
import lombok.Getter;

@Getter
public class FeatureNotIncludedException extends RuntimeException {

    private final Feature feature;
    private final String planId;
    /** The lowest plan that includes the feature, null when no plan does. */
    private final String upgradePlanId;
    /** MASTER when the plan of the game master decided (planId is then that plan), ACCOUNT otherwise. */
    private final PlanDecider decidedBy;

    public FeatureNotIncludedException(Feature feature, String planId, String upgradePlanId, PlanDecider decidedBy) {
        super("The feature " + feature.key() + " is not included in the plan " + planId);
        this.feature = feature;
        this.planId = planId;
        this.upgradePlanId = upgradePlanId;
        this.decidedBy = decidedBy;
    }
}
