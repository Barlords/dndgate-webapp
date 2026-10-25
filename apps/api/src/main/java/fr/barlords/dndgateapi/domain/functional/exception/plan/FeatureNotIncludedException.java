package fr.barlords.dndgateapi.domain.functional.exception.plan;

import fr.barlords.dndgateapi.domain.functional.model.plan.Feature;
import lombok.Getter;

@Getter
public class FeatureNotIncludedException extends RuntimeException {

    private final Feature feature;
    private final String planId;
    /** The lowest plan that includes the feature, null when no plan does. */
    private final String upgradePlanId;

    public FeatureNotIncludedException(Feature feature, String planId, String upgradePlanId) {
        super("The feature " + feature.key() + " is not included in the plan " + planId);
        this.feature = feature;
        this.planId = planId;
        this.upgradePlanId = upgradePlanId;
    }
}
