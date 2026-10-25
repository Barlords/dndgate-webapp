package fr.barlords.dndgateapi.server.adapter;

import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Price;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PriceListParams;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.billingportal.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.AutomaticTax;
import com.stripe.param.checkout.SessionCreateParams.ConsentCollection;
import com.stripe.param.checkout.SessionCreateParams.CustomText;
import com.stripe.param.checkout.SessionCreateParams.LineItem;
import com.stripe.param.checkout.SessionCreateParams.Locale;
import com.stripe.param.checkout.SessionCreateParams.Mode;
import com.stripe.param.checkout.SessionCreateParams.SubscriptionData;
import fr.barlords.dndgateapi.domain.functional.exception.plan.InvalidWebhookSignatureException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.PaymentProviderUnavailableException;
import fr.barlords.dndgateapi.domain.functional.model.plan.BillingEvent;
import fr.barlords.dndgateapi.domain.functional.model.plan.BillingInterval;
import fr.barlords.dndgateapi.domain.functional.model.plan.CheckoutRequest;
import fr.barlords.dndgateapi.domain.functional.model.plan.ProviderSubscription;
import fr.barlords.dndgateapi.domain.functional.model.plan.SubscriptionStatus;
import fr.barlords.dndgateapi.domain.port.out.PaymentProviderSpi;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stripe behind {@link PaymentProviderSpi}. Without a client (no secret key) the adapter is disabled and every
 * call but {@link #isEnabled()} fails with {@link PaymentProviderUnavailableException}.
 */
@Slf4j
public class StripePaymentAdapter implements PaymentProviderSpi {

    // Stripe refuses an expiry under 30 minutes from the moment it receives the request: keep a margin
    private static final Duration CHECKOUT_LIFETIME = Duration.ofMinutes(31);
    private static final long SUBSCRIPTION_PAGE_SIZE = 100L;
    private static final String ACCOUNT_ID_KEY = "account_id";
    private static final String PLAN_ID_KEY = "plan_id";

    private final StripeClient client;
    private final String webhookSecret;
    private final String termsAcceptanceMessage;
    private final Clock clock;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public StripePaymentAdapter(StripeClient client, String webhookSecret, String termsAcceptanceMessage, Clock clock) {
        this.client = client;
        this.webhookSecret = webhookSecret;
        this.termsAcceptanceMessage = termsAcceptanceMessage;
        this.clock = clock;
    }

    @Override
    public boolean isEnabled() {
        return client != null;
    }

    @Override
    public String createCustomer(UUID accountId, String email) {
        StripeClient stripe = enabledClient();
        try {
            return stripe.customers().create(CustomerCreateParams.builder()
                    .setEmail(email)
                    .putMetadata(ACCOUNT_ID_KEY, accountId.toString())
                    .build()).getId();
        } catch (StripeException e) {
            throw unavailable("create a customer", e);
        }
    }

    @Override
    public List<ProviderSubscription> findSubscriptions(String customerId) {
        StripeClient stripe = enabledClient();
        try {
            // The product is not expanded (Stripe allows 4 levels at most, this would need 5): it is fetched below.
            // All the pages are read, canceled subscriptions stay listed for ever with status=all.
            Iterable<Subscription> subscriptions = stripe.subscriptions().list(SubscriptionListParams.builder()
                    .setCustomer(customerId)
                    .setStatus(SubscriptionListParams.Status.ALL)
                    .setLimit(SUBSCRIPTION_PAGE_SIZE)
                    .build()).autoPagingIterable();
            Map<String, String> planIdByProduct = new HashMap<>();
            List<ProviderSubscription> result = new ArrayList<>();
            for (Subscription subscription : subscriptions) {
                toProviderSubscription(stripe, subscription, planIdByProduct).ifPresent(result::add);
            }
            return result;
        } catch (StripeException e) {
            throw unavailable("list the subscriptions", e);
        } catch (RuntimeException e) {
            // the paging iterator reports a failing next page as an unchecked exception wrapping the Stripe one
            if (e.getCause() instanceof StripeException cause) {
                throw unavailable("list the subscriptions", cause);
            }
            throw e;
        }
    }

    private String planIdOf(StripeClient stripe, String productId, Map<String, String> planIdByProduct)
            throws StripeException {
        if (productId == null) {
            return null;
        }
        if (!planIdByProduct.containsKey(productId)) {
            Map<String, String> metadata = stripe.products().retrieve(productId).getMetadata();
            planIdByProduct.put(productId, metadata == null ? null : metadata.get(PLAN_ID_KEY));
        }
        return planIdByProduct.get(productId);
    }

    private Optional<ProviderSubscription> toProviderSubscription(
            StripeClient stripe, Subscription subscription, Map<String, String> planIdByProduct) throws StripeException {
        Optional<SubscriptionStatus> status = SubscriptionStatus.fromStripe(subscription.getStatus());
        List<SubscriptionItem> items = subscription.getItems() == null ? List.of() : subscription.getItems().getData();
        if (status.isEmpty() || items.isEmpty() || items.get(0).getPrice() == null) {
            log.warn("The Stripe subscription {} has an unknown status or no price and is ignored", subscription.getId());
            return Optional.empty();
        }
        SubscriptionItem item = items.get(0);
        Price.Recurring recurring = item.getPrice().getRecurring();
        Optional<BillingInterval> interval = recurring == null ? Optional.empty() : toInterval(recurring.getInterval());
        if (interval.isEmpty()) {
            log.warn("The Stripe subscription {} is not billed monthly or yearly and is ignored", subscription.getId());
            return Optional.empty();
        }
        String planId = planIdOf(stripe, item.getPrice().getProduct(), planIdByProduct);
        Instant periodEnd = item.getCurrentPeriodEnd() == null ? null : Instant.ofEpochSecond(item.getCurrentPeriodEnd());
        return Optional.of(new ProviderSubscription(subscription.getId(), planId, interval.get(), status.get(),
                periodEnd, Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())));
    }

    private static Optional<BillingInterval> toInterval(String stripeInterval) {
        if ("month".equals(stripeInterval)) {
            return Optional.of(BillingInterval.MONTHLY);
        }
        if ("year".equals(stripeInterval)) {
            return Optional.of(BillingInterval.YEARLY);
        }
        return Optional.empty();
    }

    @Override
    public String createCheckoutSession(CheckoutRequest request) {
        StripeClient stripe = enabledClient();
        try {
            String priceId = findPriceId(stripe, request.priceLookupKey());
            String accountId = request.accountId().toString();
            return stripe.checkout().sessions().create(com.stripe.param.checkout.SessionCreateParams.builder()
                    .setMode(Mode.SUBSCRIPTION)
                    .setCustomer(request.customerId())
                    .addLineItem(LineItem.builder().setPrice(priceId).setQuantity(1L).build())
                    .setClientReferenceId(accountId)
                    .setSubscriptionData(SubscriptionData.builder().putMetadata(ACCOUNT_ID_KEY, accountId).build())
                    .setSuccessUrl(request.successUrl())
                    .setCancelUrl(request.cancelUrl())
                    .setExpiresAt(Instant.now(clock).plus(CHECKOUT_LIFETIME).getEpochSecond())
                    .setConsentCollection(ConsentCollection.builder()
                            .setTermsOfService(ConsentCollection.TermsOfService.REQUIRED).build())
                    .setCustomText(CustomText.builder()
                            .setTermsOfServiceAcceptance(CustomText.TermsOfServiceAcceptance.builder()
                                    .setMessage(termsAcceptanceMessage).build())
                            .build())
                    .setAllowPromotionCodes(false)
                    .setAutomaticTax(AutomaticTax.builder().setEnabled(false).build())
                    .setLocale(Locale.AUTO)
                    .build()).getUrl();
        } catch (StripeException e) {
            throw unavailable("create a checkout session", e);
        }
    }

    private String findPriceId(StripeClient stripe, String lookupKey) throws StripeException {
        List<Price> prices = stripe.prices().list(PriceListParams.builder()
                .addLookupKey(lookupKey)
                .setActive(true)
                .build()).getData();
        if (prices.isEmpty()) {
            log.error("Configuration error: Stripe has no active price with the lookup key {}", lookupKey);
            throw new PaymentProviderUnavailableException("No active price for the lookup key " + lookupKey);
        }
        return prices.get(0).getId();
    }

    @Override
    public String createPortalSession(String customerId, String returnUrl) {
        StripeClient stripe = enabledClient();
        try {
            return stripe.billingPortal().sessions().create(SessionCreateParams.builder()
                    .setCustomer(customerId)
                    .setReturnUrl(returnUrl)
                    .build()).getUrl();
        } catch (StripeException e) {
            throw unavailable("create a portal session", e);
        }
    }

    @Override
    public BillingEvent parseWebhookEvent(String payload, String signatureHeader) {
        enabledClient();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Configuration error: no Stripe webhook secret, the webhook cannot be verified");
            throw new PaymentProviderUnavailableException("The webhook secret is not configured");
        }
        if (payload == null || signatureHeader == null || signatureHeader.isBlank()) {
            throw new InvalidWebhookSignatureException("The webhook signature is missing");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new InvalidWebhookSignatureException("The webhook signature does not match", e);
        } catch (RuntimeException e) {
            // a malformed header or a signed but unreadable payload: nothing we can trust
            throw new InvalidWebhookSignatureException("The webhook could not be verified", e);
        }
        return new BillingEvent(event.getType(), customerOf(payload));
    }

    /** Read from the raw payload so that it does not depend on the API version the SDK was generated for. */
    private String customerOf(String payload) {
        JsonNode customer = jsonMapper.readTree(payload).path("data").path("object").path("customer");
        return customer.isString() ? customer.asString() : null;
    }

    @Override
    public void cancelNow(String subscriptionId) {
        StripeClient stripe = enabledClient();
        try {
            stripe.subscriptions().cancel(subscriptionId);
        } catch (StripeException e) {
            throw unavailable("cancel a subscription", e);
        }
    }

    private StripeClient enabledClient() {
        if (client == null) {
            throw new PaymentProviderUnavailableException("Billing is disabled: no Stripe secret key");
        }
        return client;
    }

    private static PaymentProviderUnavailableException unavailable(String action, StripeException e) {
        log.warn("Stripe failed to {} (status {}, code {})", action, e.getStatusCode(), e.getCode());
        return new PaymentProviderUnavailableException("Stripe failed to " + action, e);
    }
}
