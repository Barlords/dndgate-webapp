package fr.barlords.dndgateapi.domain.port.in.plan;

import fr.barlords.dndgateapi.domain.functional.model.plan.BillingInterval;
import fr.barlords.dndgateapi.domain.functional.model.plan.SubscriptionOverview;

import java.util.UUID;

/** Buying and managing a subscription through the payment provider. */
public interface BillingApi {

    /** The effective plan of the account, its subscription and the usage of its account limits. */
    SubscriptionOverview mine(UUID accountId);

    /**
     * Opens a hosted payment page for the plan and returns its URL.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.plan.PaymentProviderUnavailableException when billing is off
     * @throws fr.barlords.dndgateapi.domain.functional.exception.plan.PlanNotPurchasableException when the plan has no price for the interval
     * @throws fr.barlords.dndgateapi.domain.functional.exception.plan.SubscriptionAlreadyActiveException when a subscription is ongoing
     */
    String startCheckout(UUID accountId, String planId, BillingInterval interval);

    /**
     * Opens the hosted page where the account manages its subscription and returns its URL.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.plan.NoBillingAccountException when the account has no customer
     */
    String openPortal(UUID accountId);

    /**
     * Verifies a notification of the payment provider and records the current state of the subscriptions it is about.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.plan.InvalidWebhookSignatureException when the signature is missing or wrong
     */
    void handleWebhook(String payload, String signatureHeader);
}
