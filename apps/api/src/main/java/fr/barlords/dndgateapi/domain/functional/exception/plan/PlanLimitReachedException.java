package fr.barlords.dndgateapi.domain.functional.exception.plan;

import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import lombok.Getter;

@Getter
public class PlanLimitReachedException extends RuntimeException {

    private final PlanLimit limit;
    private final int max;
    private final String planId;
    /** The lowest plan that raises the limit, null when no plan does. */
    private final String upgradePlanId;

    public PlanLimitReachedException(PlanLimit limit, int max, String planId, String upgradePlanId) {
        super("The limit " + limit.key() + " of the plan " + planId + " is reached (" + max + ")");
        this.limit = limit;
        this.max = max;
        this.planId = planId;
        this.upgradePlanId = upgradePlanId;
    }
}
