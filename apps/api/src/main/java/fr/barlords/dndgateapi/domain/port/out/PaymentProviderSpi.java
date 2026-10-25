package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.plan.BillingEvent;
import fr.barlords.dndgateapi.domain.functional.model.plan.CheckoutRequest;
import fr.barlords.dndgateapi.domain.functional.model.plan.ProviderSubscription;

import java.util.List;
import java.util.UUID;

/**
 * The payment provider. Every call but {@link #isEnabled()} throws
 * {@code PaymentProviderUnavailableException} when the provider is disabled or fails.
 */
public interface PaymentProviderSpi {

    boolean isEnabled();

    /** Creates a customer and returns its provider id. */
    String createCustomer(UUID accountId, String email);

    /** All the subscriptions of the customer, whatever their status. */
    List<ProviderSubscription> findSubscriptions(String customerId);

    /** Returns the URL of the hosted payment page. */
    String createCheckoutSession(CheckoutRequest request);

    /** Returns the URL of the hosted page where the customer manages the subscription. */
    String createPortalSession(String customerId, String returnUrl);

    /**
     * Checks the signature and reads the event.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.plan.InvalidWebhookSignatureException
     *         when the signature is missing or wrong
     */
    BillingEvent parseWebhookEvent(String payload, String signatureHeader);

    /** Ends the subscription immediately. */
    void cancelNow(String subscriptionId);

    /** Expires every hosted payment page of the customer that can still be paid. */
    void expireOpenCheckoutSessions(String customerId);
}
