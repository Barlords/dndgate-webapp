package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.domain.functional.exception.plan.FeatureNotIncludedException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.PlanLimitReachedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlanErrorHandler {

    @ExceptionHandler(PlanLimitReachedException.class)
    public ProblemDetail handlePlanLimitReachedException(PlanLimitReachedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setType(URI.create("urn:dndgate:plan-limit-reached"));
        problem.setProperty("limit", e.getLimit().key());
        problem.setProperty("max", e.getMax());
        problem.setProperty("planId", e.getPlanId());
        problem.setProperty("upgradePlanId", e.getUpgradePlanId());
        return problem;
    }

    @ExceptionHandler(FeatureNotIncludedException.class)
    public ProblemDetail handleFeatureNotIncludedException(FeatureNotIncludedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        problem.setType(URI.create("urn:dndgate:feature-not-included"));
        problem.setProperty("feature", e.getFeature().key());
        problem.setProperty("planId", e.getPlanId());
        problem.setProperty("upgradePlanId", e.getUpgradePlanId());
        return problem;
    }

}
