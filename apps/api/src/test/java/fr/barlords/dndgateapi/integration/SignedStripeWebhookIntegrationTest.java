package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.server.adapter.FakeStripeServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

/**
 * The webhook as Stripe calls it: the real payment provider (the Stripe adapter built by the configuration from its
 * properties), events signed with the webhook secret, and a local stand-in for the Stripe API. Its own application
 * context: nothing here is a mock of the payment provider.
 */
@TestPropertySource(properties = {
        "stripe.secret-key=sk_test_signed_webhook",
        "stripe.webhook-secret=" + SignedStripeWebhookIntegrationTest.WEBHOOK_SECRET})
class SignedStripeWebhookIntegrationTest extends IntegrationTestBase {

    static final String WEBHOOK_SECRET = "whsec_signed_webhook_test";

    // Started once for the context, which keeps pointing to it; every test starts it empty
    private static final FakeStripeServer STRIPE = new FakeStripeServer();

    @DynamicPropertySource
    static void stripe(DynamicPropertyRegistry registry) {
        registry.add("stripe.api-base", STRIPE::baseUrl);
    }

    private TestAccount account;
    private String customer;

    @BeforeEach
    void customerWithoutSubscription() {
        STRIPE.reset();
        account = registerAndLogin();
        customer = "cus_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("insert into subscription (account_id, stripe_customer_id, cancel_at_period_end, updated_at) "
                + "values (?, ?, false, now())", account.id(), customer);
        STRIPE.answer("GET", "/v1/products/prod_paid",
                "{\"id\":\"prod_paid\",\"object\":\"product\",\"metadata\":{\"plan_id\":\"test-paid\"}}");
    }

    /** What Stripe answers now about the subscriptions of the customer: one subscription to the paid plan. */
    private void stripeSays(String status) {
        STRIPE.answer("GET", "/v1/subscriptions", "{\"object\":\"list\",\"url\":\"/v1/subscriptions\",\"has_more\":false,"
                + "\"data\":[{\"id\":\"sub_" + account.id() + "\",\"object\":\"subscription\",\"status\":\"" + status + "\","
                + "\"customer\":\"" + customer + "\",\"cancel_at_period_end\":false,"
                + "\"items\":{\"object\":\"list\",\"url\":\"/v1/subscription_items\",\"has_more\":false,\"data\":["
                + "{\"id\":\"si_1\",\"object\":\"subscription_item\",\"current_period_end\":1900000000,"
                + "\"price\":{\"id\":\"price_1\",\"object\":\"price\",\"product\":\"prod_paid\","
                + "\"recurring\":{\"interval\":\"month\"}}}]}}]}");
    }

    private String event(String type, String object) {
        return "{\"id\":\"evt_" + UUID.randomUUID() + "\",\"object\":\"event\",\"type\":\"" + type + "\","
                + "\"data\":{\"object\":" + object + "}}";
    }

    private String subscriptionEvent(String type, String status) {
        return event(type, "{\"id\":\"sub_" + account.id() + "\",\"object\":\"subscription\",\"customer\":\""
                + customer + "\",\"status\":\"" + status + "\"}");
    }

    private ResponseEntity<String> post(String payload, String signature) {
        return client().post().uri("/billing/stripe-webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> signedPost(String payload) {
        return post(payload, FakeStripeServer.signature(payload, WEBHOOK_SECRET, System.currentTimeMillis() / 1000));
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap("select plan_id, status, stripe_subscription_id, updated_at from subscription "
                + "where account_id = ?", account.id());
    }

    private String effectivePlan() {
        JsonNode mine = body(call(HttpMethod.GET, "/accounts/me/subscription", account.token(), null));
        Assertions.assertTrue(mine.get("billingEnabled").asBoolean(), "the real adapter, with a secret key");
        return mine.get("planId").asString();
    }

    @Test
    void a_signed_completed_checkout_grants_the_plan_and_the_same_event_twice_changes_nothing() {
        stripeSays("active");
        String completed = event("checkout.session.completed",
                "{\"id\":\"cs_1\",\"object\":\"checkout.session\",\"customer\":\"" + customer + "\"}");

        ResponseEntity<String> first = signedPost(completed);

        Assertions.assertEquals(200, first.getStatusCode().value(), first.getBody());
        Map<String, Object> granted = row();
        Assertions.assertEquals("test-paid", granted.get("plan_id"));
        Assertions.assertEquals("ACTIVE", granted.get("status"));
        Assertions.assertEquals("sub_" + account.id(), granted.get("stripe_subscription_id"));
        Assertions.assertEquals("test-paid", effectivePlan());
        Assertions.assertEquals(customer, STRIPE.only("GET", "/v1/subscriptions").form().get("customer"));

        ResponseEntity<String> again = signedPost(completed);

        Assertions.assertEquals(200, again.getStatusCode().value(), again.getBody());
        Assertions.assertEquals(granted, row(), "the row already says what Stripe says: not even written again");
        Assertions.assertEquals("test-paid", effectivePlan());
    }

    @Test
    void a_deletion_received_before_an_older_update_leaves_what_stripe_says_now() {
        stripeSays("active");
        Assertions.assertEquals(200, signedPost(subscriptionEvent("customer.subscription.created", "active"))
                .getStatusCode().value());
        Assertions.assertEquals("test-paid", effectivePlan());

        // the subscription is over at Stripe; its deletion arrives first, the update sent before it arrives last
        STRIPE.reset();
        STRIPE.answer("GET", "/v1/products/prod_paid",
                "{\"id\":\"prod_paid\",\"object\":\"product\",\"metadata\":{\"plan_id\":\"test-paid\"}}");
        stripeSays("canceled");
        Assertions.assertEquals(200, signedPost(subscriptionEvent("customer.subscription.deleted", "canceled"))
                .getStatusCode().value());
        Assertions.assertEquals("CANCELED", row().get("status"), "the deletion is recorded");
        ResponseEntity<String> late = signedPost(subscriptionEvent("customer.subscription.updated", "active"));

        Assertions.assertEquals(200, late.getStatusCode().value(), late.getBody());
        Assertions.assertEquals("CANCELED", row().get("status"), "the late update does not bring the plan back");
        Assertions.assertEquals("test-default", effectivePlan());
    }

    @Test
    void a_forged_signature_answers_400_and_writes_nothing() {
        stripeSays("active");
        String payload = event("checkout.session.completed",
                "{\"id\":\"cs_1\",\"object\":\"checkout.session\",\"customer\":\"" + customer + "\"}");
        long now = System.currentTimeMillis() / 1000;
        Timestamp before = (Timestamp) row().get("updated_at");

        for (String signature : new String[]{
                FakeStripeServer.signature(payload, "whsec_someone_else", now),
                FakeStripeServer.signature(payload.replace("cs_1", "cs_2"), WEBHOOK_SECRET, now),
                "t=" + now + ",v1=" + "0".repeat(64)}) {
            ResponseEntity<String> refused = post(payload, signature);

            Assertions.assertEquals(400, refused.getStatusCode().value(), refused.getBody());
            Assertions.assertEquals("urn:dndgate:invalid-webhook-signature", body(refused).get("type").asString());
        }

        Assertions.assertNull(row().get("status"));
        Assertions.assertEquals(before, row().get("updated_at"));
        Assertions.assertTrue(STRIPE.calls().isEmpty(), "Stripe is not even asked");
        Assertions.assertEquals("test-default", effectivePlan());
    }
}
