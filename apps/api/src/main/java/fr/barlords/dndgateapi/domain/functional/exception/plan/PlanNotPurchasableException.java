package fr.barlords.dndgateapi.domain.functional.exception.plan;

import fr.barlords.dndgateapi.domain.functional.model.plan.BillingInterval;
import lombok.Getter;

/** The plan does not exist, is free, or has no price for the interval. */
@Getter
public class PlanNotPurchasableException extends RuntimeException {

    private final String planId;

    public PlanNotPurchasableException(String planId, BillingInterval interval) {
        super("The plan " + planId + " cannot be bought " + (interval == null ? "without an interval" : interval.key()));
        this.planId = planId;
    }
}
