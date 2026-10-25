package fr.barlords.dndgateapi.domain.functional.exception.plan;

import fr.barlords.dndgateapi.domain.functional.model.plan.PlanDecider;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import lombok.Getter;

@Getter
public class PlanLimitReachedException extends RuntimeException {

    private final PlanLimit limit;
    private final int max;
    private final String planId;
    /** The lowest plan that raises the limit, null when no plan does. */
    private final String upgradePlanId;
    /** ACCOUNT for a limit of the account, MASTER for a limit of an adventure (planId is then the plan of its master). */
    private final PlanDecider decidedBy;

    public PlanLimitReachedException(PlanLimit limit, int max, String planId, String upgradePlanId,
                                     PlanDecider decidedBy) {
        super("The limit " + limit.key() + " of the plan " + planId + " is reached (" + max + ")");
        this.limit = limit;
        this.max = max;
        this.planId = planId;
        this.upgradePlanId = upgradePlanId;
        this.decidedBy = decidedBy;
    }
}
