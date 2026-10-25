package fr.barlords.dndgateapi.client.error;

import fr.barlords.dndgateapi.domain.functional.exception.plan.FeatureNotIncludedException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.InvalidWebhookSignatureException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.NoBillingAccountException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.PaymentProviderUnavailableException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.PlanLimitReachedException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.PlanNotPurchasableException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.SubscriptionAlreadyActiveException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.SubscriptionSyncConflictException;
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
        ProblemDetail problem = problem(HttpStatus.CONFLICT, e.getMessage(), "urn:dndgate:plan-limit-reached");
        problem.setProperty("limit", e.getLimit().key());
        problem.setProperty("max", e.getMax());
        problem.setProperty("planId", e.getPlanId());
        problem.setProperty("upgradePlanId", e.getUpgradePlanId());
        return problem;
    }

    @ExceptionHandler(FeatureNotIncludedException.class)
    public ProblemDetail handleFeatureNotIncludedException(FeatureNotIncludedException e) {
        ProblemDetail problem = problem(HttpStatus.FORBIDDEN, e.getMessage(), "urn:dndgate:feature-not-included");
        problem.setProperty("feature", e.getFeature().key());
        problem.setProperty("planId", e.getPlanId());
        problem.setProperty("upgradePlanId", e.getUpgradePlanId());
        return problem;
    }

    /** The cause (billing off, a failing Stripe call) is logged by the adapter, not sent. */
    @ExceptionHandler(PaymentProviderUnavailableException.class)
    public ProblemDetail handlePaymentProviderUnavailableException() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Billing is not available at the moment",
                "urn:dndgate:payment-provider-unavailable");
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    public ProblemDetail handleInvalidWebhookSignatureException() {
        return problem(HttpStatus.BAD_REQUEST, "The webhook signature is missing or does not match",
                "urn:dndgate:invalid-webhook-signature");
    }

    /** A server error on purpose: Stripe sends the notification again, and the next sync reads everything again. */
    @ExceptionHandler(SubscriptionSyncConflictException.class)
    public ProblemDetail handleSubscriptionSyncConflictException(SubscriptionSyncConflictException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), "urn:dndgate:subscription-sync-conflict");
    }

    @ExceptionHandler(SubscriptionAlreadyActiveException.class)
    public ProblemDetail handleSubscriptionAlreadyActiveException(SubscriptionAlreadyActiveException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage(), "urn:dndgate:subscription-already-active");
    }

    @ExceptionHandler(NoBillingAccountException.class)
    public ProblemDetail handleNoBillingAccountException(NoBillingAccountException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage(), "urn:dndgate:no-billing-account");
    }

    @ExceptionHandler(PlanNotPurchasableException.class)
    public ProblemDetail handlePlanNotPurchasableException(PlanNotPurchasableException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, e.getMessage(), "urn:dndgate:plan-not-purchasable");
        problem.setProperty("planId", e.getPlanId());
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        return problem;
    }
}
