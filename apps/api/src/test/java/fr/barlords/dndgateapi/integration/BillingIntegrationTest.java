package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.functional.exception.plan.InvalidWebhookSignatureException;
import fr.barlords.dndgateapi.domain.functional.model.plan.BillingEvent;
import fr.barlords.dndgateapi.domain.functional.model.plan.BillingInterval;
import fr.barlords.dndgateapi.domain.functional.model.plan.CheckoutRequest;
import fr.barlords.dndgateapi.domain.functional.model.plan.ProviderSubscription;
import fr.barlords.dndgateapi.domain.functional.model.plan.SubscriptionStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The billing routes against the real database, the payment provider being a mock. */
class BillingIntegrationTest extends ApiIntegrationTestSupport {

    private static final String WEB = "http://localhost:4200";

    private ResponseEntity<String> webhook(String payload, String signature) {
        return client().post().uri("/billing/stripe-webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (signature != null) {
                        headers.set("Stripe-Signature", signature);
                    }
                })
                .body(payload)
                .retrieve().toEntity(String.class);
    }

    private void assertProblem(ResponseEntity<String> response, int status, String type) {
        Assertions.assertEquals(status, response.getStatusCode().value(), response.getBody());
        Assertions.assertTrue(response.getHeaders().getContentType().toString().contains("problem+json"), response.getBody());
        if (type != null) {
            Assertions.assertEquals(type, body(response).get("type").asString());
        }
    }

    private ResponseEntity<String> checkout(TestAccount account, Object request) {
        return call(HttpMethod.POST, "/billing/checkout-session", account.token(), request);
    }

    private static ProviderSubscription active(String planId) {
        return new ProviderSubscription("sub_" + UUID.randomUUID(), planId, BillingInterval.MONTHLY,
                SubscriptionStatus.ACTIVE, Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS), false);
    }

    @Test
    void the_catalog_is_public_and_hides_the_lookup_keys() {
        ResponseEntity<String> response = call(HttpMethod.GET, "/plans", null, null);

        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
        Assertions.assertFalse(response.getBody().contains("stripeLookupKey"), response.getBody());
        Assertions.assertFalse(response.getBody().contains("test_paid_monthly"), response.getBody());
        JsonNode catalog = body(response);
        Assertions.assertEquals("EUR", catalog.get("currency").asString());
        Assertions.assertEquals("test-default", catalog.get("defaultPlan").asString());
        List<String> ids = new ArrayList<>();
        catalog.get("plans").forEach(plan -> ids.add(plan.get("id").asString()));
        Assertions.assertEquals(List.of("test-default", "test-chat", "test-tight", "test-paid"), ids);

        JsonNode free = catalog.get("plans").get(0);
        Assertions.assertTrue(free.get("prices").isEmpty());
        Assertions.assertTrue(free.get("badge").isNull());
        JsonNode paid = catalog.get("plans").get(3);
        Assertions.assertEquals(500, paid.get("prices").get("monthly").get("amount").asInt());
        Assertions.assertEquals(5000, paid.get("prices").get("yearly").get("amount").asInt());
        Assertions.assertEquals(1, paid.get("prices").get("monthly").size());
        Assertions.assertEquals(1000, paid.get("limits").get("spellBooks").asInt());
        Assertions.assertEquals(9, paid.get("limits").size());
        Assertions.assertTrue(paid.get("features").get("equippedTable").asBoolean());
        Assertions.assertEquals(5, paid.get("features").size());
        Assertions.assertEquals("legend", paid.get("badge").asString());
        Assertions.assertFalse(paid.get("highlighted").asBoolean());
        Assertions.assertFalse(catalog.get("plans").get(2).get("features").get("video").asBoolean());
    }

    @Test
    void my_subscription_shows_plan_usage_and_billing_state() {
        TestAccount account = registerAndLogin();
        createAdventure(account, "PUBLIC");

        ResponseEntity<String> response = call(HttpMethod.GET, "/accounts/me/subscription", account.token(), null);
        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
        JsonNode mine = body(response);
        Assertions.assertEquals("test-default", mine.get("planId").asString());
        Assertions.assertTrue(mine.get("interval").isNull());
        Assertions.assertTrue(mine.get("status").isNull());
        Assertions.assertTrue(mine.get("currentPeriodEnd").isNull());
        Assertions.assertFalse(mine.get("cancelAtPeriodEnd").asBoolean());
        Assertions.assertFalse(mine.get("billingEnabled").asBoolean());
        Assertions.assertEquals(4, mine.get("usage").size());
        Assertions.assertEquals(1, mine.get("usage").get("activeAdventuresAsMaster").get("used").asInt());
        Assertions.assertEquals(1000, mine.get("usage").get("activeAdventuresAsMaster").get("max").asInt());
        Assertions.assertEquals(0, mine.get("usage").get("spellBooks").get("used").asInt());

        subscribe(account, "test-tight");
        jdbc.update("update subscription set billing_interval = 'MONTHLY', current_period_end = now(), "
                + "cancel_at_period_end = true where account_id = ?", account.id());
        when(paymentProviderSpi.isEnabled()).thenReturn(true);

        mine = body(call(HttpMethod.GET, "/accounts/me/subscription", account.token(), null));
        Assertions.assertEquals("test-tight", mine.get("planId").asString());
        Assertions.assertEquals("monthly", mine.get("interval").asString());
        Assertions.assertEquals("active", mine.get("status").asString());
        Assertions.assertFalse(mine.get("currentPeriodEnd").isNull());
        Assertions.assertTrue(mine.get("cancelAtPeriodEnd").asBoolean());
        Assertions.assertTrue(mine.get("billingEnabled").asBoolean());
        Assertions.assertEquals(1, mine.get("usage").get("activeAdventuresAsMaster").get("max").asInt());

        Assertions.assertEquals(401, call(HttpMethod.GET, "/accounts/me/subscription", null, null).getStatusCode().value());
    }

    @Test
    void checkout_answers_the_stripe_url() {
        TestAccount account = registerAndLogin();
        String customer = "cus_" + UUID.randomUUID();
        when(paymentProviderSpi.isEnabled()).thenReturn(true);
        when(paymentProviderSpi.createCustomer(account.id(), account.pseudo() + "@test.fr")).thenReturn(customer);
        when(paymentProviderSpi.findSubscriptions(customer)).thenReturn(List.of());
        when(paymentProviderSpi.createCheckoutSession(any())).thenReturn("https://checkout.stripe.test/c/1");

        ResponseEntity<String> response = checkout(account, Map.of("planId", "test-paid", "interval", "yearly"));

        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals("https://checkout.stripe.test/c/1", body(response).get("url").asString());
        ArgumentCaptor<CheckoutRequest> request = ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(paymentProviderSpi).createCheckoutSession(request.capture());
        Assertions.assertEquals(new CheckoutRequest(customer, account.id(), "test_paid_yearly",
                WEB + "/plans/success", WEB + "/plans"), request.getValue());
        Assertions.assertEquals(customer, jdbc.queryForObject(
                "select stripe_customer_id from subscription where account_id = ?", String.class, account.id()));
        Assertions.assertNull(jdbc.queryForObject(
                "select status from subscription where account_id = ?", String.class, account.id()));
        // still on the free plan: only the webhook gives a plan
        Assertions.assertEquals("test-default",
                body(call(HttpMethod.GET, "/accounts/me/subscription", account.token(), null)).get("planId").asString());

        // an ongoing subscription at Stripe: the portal, not a second checkout
        when(paymentProviderSpi.findSubscriptions(customer)).thenReturn(List.of(active("test-paid")));
        assertProblem(checkout(account, Map.of("planId", "test-paid", "interval", "monthly")), 409,
                "urn:dndgate:subscription-already-active");

        // the portal of the customer
        when(paymentProviderSpi.createPortalSession(customer, WEB + "/account")).thenReturn("https://portal.stripe.test/p/1");
        ResponseEntity<String> portal = call(HttpMethod.POST, "/billing/portal-session", account.token(), null);
        Assertions.assertEquals(201, portal.getStatusCode().value(), portal.getBody());
        Assertions.assertEquals("https://portal.stripe.test/p/1", body(portal).get("url").asString());
    }

    @Test
    void a_checkout_request_is_validated() {
        TestAccount account = registerAndLogin();
        when(paymentProviderSpi.isEnabled()).thenReturn(true);

        assertProblem(checkout(account, Map.of("planId", "test-paid", "interval", "weekly")), 400, null);
        assertProblem(checkout(account, Map.of("planId", "test-paid")), 400, null);
        assertProblem(checkout(account, Map.of("interval", "monthly")), 400, null);
        assertProblem(checkout(account, Map.of("planId", "x".repeat(51), "interval", "monthly")), 400, null);
        assertProblem(checkout(account, Map.of("planId", "test-default", "interval", "monthly")), 400,
                "urn:dndgate:plan-not-purchasable");
        assertProblem(checkout(account, Map.of("planId", "test-chat", "interval", "yearly")), 400,
                "urn:dndgate:plan-not-purchasable");
        verify(paymentProviderSpi, never()).createCustomer(any(), any());
    }

    @Test
    void the_portal_needs_a_stripe_customer() {
        TestAccount account = registerAndLogin();
        when(paymentProviderSpi.isEnabled()).thenReturn(true);

        assertProblem(call(HttpMethod.POST, "/billing/portal-session", account.token(), null), 409,
                "urn:dndgate:no-billing-account");
    }

    @Test
    void checkout_without_stripe_answers_503() {
        TestAccount account = registerAndLogin();
        subscribe(account, "test-tight");

        assertProblem(checkout(account, Map.of("planId", "test-paid", "interval", "monthly")), 503,
                "urn:dndgate:payment-provider-unavailable");
        assertProblem(call(HttpMethod.POST, "/billing/portal-session", account.token(), null), 503,
                "urn:dndgate:payment-provider-unavailable");
        assertProblem(webhook("{}", "t=1,v1=abc"), 503, "urn:dndgate:payment-provider-unavailable");
    }

    @Test
    void the_webhook_is_public_and_refuses_a_bad_signature() {
        when(paymentProviderSpi.isEnabled()).thenReturn(true);
        when(paymentProviderSpi.parseWebhookEvent("{\"id\":\"evt_1\"}", "t=1,v1=forged"))
                .thenThrow(new InvalidWebhookSignatureException("The webhook signature does not match"));
        when(paymentProviderSpi.parseWebhookEvent("{\"id\":\"evt_2\"}", "t=1,v1=good"))
                .thenReturn(new BillingEvent("invoice.paid", "cus_whatever"));

        assertProblem(webhook("{\"id\":\"evt_1\"}", "t=1,v1=forged"), 400, "urn:dndgate:invalid-webhook-signature");

        // no signature at all: refused before anything is read
        assertProblem(webhook("{\"id\":\"evt_3\"}", null), 400, null);
        verify(paymentProviderSpi, never()).parseWebhookEvent(any(), org.mockito.ArgumentMatchers.isNull());

        // a signed event without effect is acknowledged, without any token
        ResponseEntity<String> ignored = webhook("{\"id\":\"evt_2\"}", "t=1,v1=good");
        Assertions.assertEquals(200, ignored.getStatusCode().value(), ignored.getBody());
        verify(paymentProviderSpi, never()).findSubscriptions(any());
    }

    @Test
    void a_webhook_event_changes_the_effective_plan() {
        TestAccount account = registerAndLogin();
        subscribe(account, "test-tight");
        String customer = "cus_test_" + account.id();
        createAdventure(account, "PUBLIC");
        Assertions.assertEquals(409, call(HttpMethod.POST, "/adventures", account.token(), Map.of(
                "name", "Quest", "description", "A quest", "maxPlayer", 4,
                "visibility", "PUBLIC", "postalCode", "75001", "city", "Paris")).getStatusCode().value());

        ProviderSubscription paid = active("test-paid");
        when(paymentProviderSpi.isEnabled()).thenReturn(true);
        when(paymentProviderSpi.parseWebhookEvent("{\"id\":\"evt_paid\"}", "t=1,v1=good"))
                .thenReturn(new BillingEvent("customer.subscription.updated", customer));
        when(paymentProviderSpi.findSubscriptions(customer)).thenReturn(List.of(paid));

        ResponseEntity<String> response = webhook("{\"id\":\"evt_paid\"}", "t=1,v1=good");

        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
        JsonNode mine = body(call(HttpMethod.GET, "/accounts/me/subscription", account.token(), null));
        Assertions.assertEquals("test-paid", mine.get("planId").asString());
        Assertions.assertEquals("active", mine.get("status").asString());
        Assertions.assertEquals(paid.currentPeriodEnd(), Instant.parse(mine.get("currentPeriodEnd").asString()));
        Assertions.assertEquals(paid.id(), jdbc.queryForObject(
                "select stripe_subscription_id from subscription where account_id = ?", String.class, account.id()));
        createAdventure(account, "PUBLIC");
    }
}
