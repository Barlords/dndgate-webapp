package fr.barlords.dndgateapi.server.adapter;

import com.stripe.StripeClient;
import fr.barlords.dndgateapi.domain.functional.exception.plan.InvalidWebhookSignatureException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.PaymentProviderUnavailableException;
import fr.barlords.dndgateapi.domain.functional.model.plan.BillingEvent;
import fr.barlords.dndgateapi.domain.functional.model.plan.BillingInterval;
import fr.barlords.dndgateapi.domain.functional.model.plan.CheckoutRequest;
import fr.barlords.dndgateapi.domain.functional.model.plan.ProviderSubscription;
import fr.barlords.dndgateapi.domain.functional.model.plan.SubscriptionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StripePaymentAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    private static final String WEBHOOK_SECRET = "whsec_test";
    private static final String TERMS = "I accept the terms";
    private static final UUID ACCOUNT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final FakeStripeServer stripe = new FakeStripeServer();

    @AfterEach
    void stop() {
        stripe.close();
    }

    private StripePaymentAdapter adapter() {
        return adapter(WEBHOOK_SECRET);
    }

    private StripePaymentAdapter adapter(String webhookSecret) {
        StripeClient client = StripeClient.builder().setApiKey("sk_test_123").setApiBase(stripe.baseUrl())
                .setMaxNetworkRetries(0).build();
        return new StripePaymentAdapter(client, webhookSecret, TERMS, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String sign(String payload, String secret, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hex = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + hex;
    }

    @Test
    void a_blank_key_disables_the_adapter() {
        StripePaymentAdapter disabled = new StripePaymentAdapter(null, WEBHOOK_SECRET, TERMS, Clock.systemUTC());

        assertFalse(disabled.isEnabled());
        assertThrows(PaymentProviderUnavailableException.class, () -> disabled.createCustomer(ACCOUNT, "a@b.c"));
        assertThrows(PaymentProviderUnavailableException.class, () -> disabled.findSubscriptions("cus_1"));
        assertThrows(PaymentProviderUnavailableException.class, () -> disabled.createCheckoutSession(
                new CheckoutRequest("cus_1", ACCOUNT, "hero_monthly", "https://ok", "https://ko")));
        assertThrows(PaymentProviderUnavailableException.class, () -> disabled.createPortalSession("cus_1", "https://back"));
        assertThrows(PaymentProviderUnavailableException.class, () -> disabled.parseWebhookEvent("{}", "t=1,v1=a"));
        assertThrows(PaymentProviderUnavailableException.class, () -> disabled.cancelNow("sub_1"));
        assertThrows(PaymentProviderUnavailableException.class, () -> disabled.expireOpenCheckoutSessions("cus_1"));
        assertTrue(adapter().isEnabled());
    }

    @Test
    void the_customer_the_portal_and_the_cancellation_reach_stripe() {
        stripe.answer("POST", "/v1/customers", "{\"id\":\"cus_9\",\"object\":\"customer\"}")
                .answer("POST", "/v1/billing_portal/sessions",
                        "{\"id\":\"bps_1\",\"object\":\"billing_portal.session\",\"url\":\"https://billing.example/p\"}")
                .answer("DELETE", "/v1/subscriptions/sub_1",
                        "{\"id\":\"sub_1\",\"object\":\"subscription\",\"status\":\"canceled\"}");
        StripePaymentAdapter adapter = adapter();

        assertEquals("cus_9", adapter.createCustomer(ACCOUNT, "player@example.org"));
        assertEquals("https://billing.example/p", adapter.createPortalSession("cus_9", "https://app/back"));
        adapter.cancelNow("sub_1");

        Map<String, String> customer = stripe.only("POST", "/v1/customers").form();
        assertEquals("player@example.org", customer.get("email"));
        assertEquals(ACCOUNT.toString(), customer.get("metadata[account_id]"));
        Map<String, String> portal = stripe.only("POST", "/v1/billing_portal/sessions").form();
        assertEquals("cus_9", portal.get("customer"));
        assertEquals("https://app/back", portal.get("return_url"));
        assertNotNull(stripe.only("DELETE", "/v1/subscriptions/sub_1"));
    }

    @Test
    void the_checkout_session_carries_the_price_the_customer_the_consent_and_the_expiry() {
        stripe.answer("GET", "/v1/prices",
                        "{\"object\":\"list\",\"url\":\"/v1/prices\",\"has_more\":false,\"data\":["
                                + "{\"id\":\"price_42\",\"object\":\"price\",\"active\":true,\"lookup_key\":\"hero_monthly\"}]}")
                .answer("POST", "/v1/checkout/sessions",
                        "{\"id\":\"cs_1\",\"object\":\"checkout.session\",\"url\":\"https://checkout.example/cs_1\"}");

        String url = adapter().createCheckoutSession(
                new CheckoutRequest("cus_9", ACCOUNT, "hero_monthly", "https://app/ok", "https://app/ko"));

        assertEquals("https://checkout.example/cs_1", url);
        Map<String, String> price = stripe.only("GET", "/v1/prices").form();
        assertEquals("hero_monthly", price.get("lookup_keys[0]"));
        assertEquals("true", price.get("active"));
        Map<String, String> form = stripe.only("POST", "/v1/checkout/sessions").form();
        assertEquals("subscription", form.get("mode"));
        assertEquals("cus_9", form.get("customer"));
        assertEquals("price_42", form.get("line_items[0][price]"));
        assertEquals("1", form.get("line_items[0][quantity]"));
        assertEquals(ACCOUNT.toString(), form.get("client_reference_id"));
        assertEquals(ACCOUNT.toString(), form.get("subscription_data[metadata][account_id]"));
        assertEquals("https://app/ok", form.get("success_url"));
        assertEquals("https://app/ko", form.get("cancel_url"));
        assertEquals(String.valueOf(NOW.plusSeconds(31 * 60).getEpochSecond()), form.get("expires_at"));
        assertEquals("required", form.get("consent_collection[terms_of_service]"));
        assertEquals(TERMS, form.get("custom_text[terms_of_service_acceptance][message]"));
        assertEquals("false", form.get("allow_promotion_codes"));
        assertEquals("false", form.get("automatic_tax[enabled]"));
        assertEquals("auto", form.get("locale"));
    }

    @Test
    void an_unknown_price_is_a_configuration_error_reported_as_unavailable() {
        stripe.answer("GET", "/v1/prices", "{\"object\":\"list\",\"url\":\"/v1/prices\",\"has_more\":false,\"data\":[]}");

        assertThrows(PaymentProviderUnavailableException.class, () -> adapter().createCheckoutSession(
                new CheckoutRequest("cus_9", ACCOUNT, "nope_monthly", "https://app/ok", "https://app/ko")));
        assertTrue(stripe.calls().stream().noneMatch(call -> call.path().equals("/v1/checkout/sessions")));
    }

    private static String page(boolean hasMore, String... subscriptions) {
        return "{\"object\":\"list\",\"url\":\"/v1/subscriptions\",\"has_more\":" + hasMore
                + ",\"data\":[" + String.join(",", subscriptions) + "]}";
    }

    private static String product(String id, String metadata) {
        return "{\"id\":\"" + id + "\",\"object\":\"product\",\"metadata\":" + metadata + "}";
    }

    @Test
    void subscriptions_are_read_with_their_plan_status_and_period_end() {
        stripe.answer("GET", "/v1/subscriptions", page(false,
                        subscription("sub_1", "past_due", true, "year", "prod_hero", 1_800_000_000L),
                        subscription("sub_2", "canceled", false, "month", "prod_none", 1_700_000_000L),
                        subscription("sub_3", "active", false, "week", "prod_hero", 1_700_000_000L),
                        subscription("sub_4", "active", false, "month", "prod_hero", 1_750_000_000L)))
                .answer("GET", "/v1/products/prod_hero", product("prod_hero", "{\"plan_id\":\"hero\"}"))
                .answer("GET", "/v1/products/prod_none", product("prod_none", "{}"));

        List<ProviderSubscription> subscriptions = adapter().findSubscriptions("cus_9");

        assertEquals(3, subscriptions.size(), "a subscription billed on an unknown interval is left out");
        assertEquals(new ProviderSubscription("sub_1", "hero", BillingInterval.YEARLY, SubscriptionStatus.PAST_DUE,
                Instant.ofEpochSecond(1_800_000_000L), true), subscriptions.get(0));
        assertEquals(new ProviderSubscription("sub_2", null, BillingInterval.MONTHLY, SubscriptionStatus.CANCELED,
                Instant.ofEpochSecond(1_700_000_000L), false), subscriptions.get(1));
        assertEquals("hero", subscriptions.get(2).planId());
        Map<String, String> query = stripe.only("GET", "/v1/subscriptions").form();
        assertEquals("cus_9", query.get("customer"));
        assertEquals("all", query.get("status"));
        assertEquals("100", query.get("limit"));
        assertTrue(query.keySet().stream().noneMatch(key -> key.startsWith("expand")),
                "the product is fetched, never expanded (Stripe allows 4 levels, this would need 5)");
        assertEquals(1, stripe.calls().stream().filter(call -> call.path().equals("/v1/products/prod_hero")).count(),
                "a product shared by several subscriptions is fetched once");
    }

    @Test
    void every_page_of_subscriptions_is_read() {
        stripe.answer("GET", "/v1/subscriptions",
                        page(true, subscription("sub_1", "canceled", false, "month", "prod_hero", 1_700_000_000L)))
                .answer("GET", "/v1/subscriptions",
                        page(false, subscription("sub_2", "active", false, "year", "prod_hero", 1_800_000_000L)))
                .answer("GET", "/v1/products/prod_hero", product("prod_hero", "{\"plan_id\":\"hero\"}"));

        List<ProviderSubscription> subscriptions = adapter().findSubscriptions("cus_9");

        assertEquals(List.of("sub_1", "sub_2"), subscriptions.stream().map(ProviderSubscription::id).toList());
        List<FakeStripeServer.Call> pages = stripe.calls().stream()
                .filter(call -> call.path().equals("/v1/subscriptions")).toList();
        assertEquals(2, pages.size());
        assertEquals("sub_1", pages.get(1).form().get("starting_after"));
    }

    @Test
    void a_failing_next_page_becomes_unavailable() {
        stripe.answer("GET", "/v1/subscriptions",
                        page(true, subscription("sub_1", "canceled", false, "month", "prod_hero", 1_700_000_000L)))
                .fail("GET", "/v1/subscriptions", 500, "{\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}")
                .answer("GET", "/v1/products/prod_hero", product("prod_hero", "{}"));

        assertThrows(PaymentProviderUnavailableException.class, () -> adapter().findSubscriptions("cus_9"));
    }

    private static String subscription(String id, String status, boolean cancelAtPeriodEnd, String interval,
                                       String productId, long periodEnd) {
        return "{\"id\":\"" + id + "\",\"object\":\"subscription\",\"status\":\"" + status + "\","
                + "\"cancel_at_period_end\":" + cancelAtPeriodEnd + ","
                + "\"items\":{\"object\":\"list\",\"url\":\"/v1/subscription_items\",\"has_more\":false,\"data\":["
                + "{\"id\":\"si_" + id + "\",\"object\":\"subscription_item\",\"current_period_end\":" + periodEnd + ","
                + "\"price\":{\"id\":\"price_" + id + "\",\"object\":\"price\",\"product\":\"" + productId + "\","
                + "\"recurring\":{\"interval\":\"" + interval + "\"}}}]}}";
    }

    private static String sessions(boolean hasMore, String... ids) {
        List<String> data = new java.util.ArrayList<>();
        for (String id : ids) {
            data.add(session(id, "open"));
        }
        return "{\"object\":\"list\",\"url\":\"/v1/checkout/sessions\",\"has_more\":" + hasMore
                + ",\"data\":[" + String.join(",", data) + "]}";
    }

    private static String session(String id, String status) {
        return "{\"id\":\"" + id + "\",\"object\":\"checkout.session\",\"status\":\"" + status + "\"}";
    }

    @Test
    void every_open_checkout_page_of_the_customer_is_expired() {
        stripe.answer("GET", "/v1/checkout/sessions", sessions(true, "cs_1"))
                .answer("GET", "/v1/checkout/sessions", sessions(false, "cs_2"))
                .answer("POST", "/v1/checkout/sessions/cs_1/expire", session("cs_1", "expired"))
                .answer("POST", "/v1/checkout/sessions/cs_2/expire", session("cs_2", "expired"));

        adapter().expireOpenCheckoutSessions("cus_9");

        List<FakeStripeServer.Call> pages = stripe.calls().stream()
                .filter(call -> call.method().equals("GET") && call.path().equals("/v1/checkout/sessions")).toList();
        assertEquals(2, pages.size(), "every page is read");
        Map<String, String> query = pages.get(0).form();
        assertEquals("cus_9", query.get("customer"));
        assertEquals("open", query.get("status"));
        assertEquals("100", query.get("limit"));
        assertEquals("cs_1", pages.get(1).form().get("starting_after"));
        assertNotNull(stripe.only("POST", "/v1/checkout/sessions/cs_1/expire"));
        assertNotNull(stripe.only("POST", "/v1/checkout/sessions/cs_2/expire"));
        // every page is read before the first expiry: the cursor never points at a session that changed meanwhile
        List<String> order = stripe.calls().stream().map(FakeStripeServer.Call::path).toList();
        assertEquals(List.of("/v1/checkout/sessions", "/v1/checkout/sessions", "/v1/checkout/sessions/cs_1/expire",
                "/v1/checkout/sessions/cs_2/expire"), order);
    }

    @Test
    void a_customer_without_an_open_checkout_page_expires_nothing() {
        stripe.answer("GET", "/v1/checkout/sessions", sessions(false));

        adapter().expireOpenCheckoutSessions("cus_9");

        assertEquals(1, stripe.calls().size());
    }

    @Test
    void a_failing_listing_or_expiry_of_the_checkout_pages_becomes_unavailable() {
        stripe.fail("GET", "/v1/checkout/sessions", 500, "{\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}");
        assertThrows(PaymentProviderUnavailableException.class, () -> adapter().expireOpenCheckoutSessions("cus_9"));

        try (FakeStripeServer other = new FakeStripeServer()) {
            other.answer("GET", "/v1/checkout/sessions", sessions(true, "cs_1"))
                    .fail("GET", "/v1/checkout/sessions", 500, "{\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}");
            StripePaymentAdapter adapter = new StripePaymentAdapter(StripeClient.builder().setApiKey("sk_test_123")
                    .setApiBase(other.baseUrl()).setMaxNetworkRetries(0).build(), WEBHOOK_SECRET, TERMS, Clock.systemUTC());
            assertThrows(PaymentProviderUnavailableException.class, () -> adapter.expireOpenCheckoutSessions("cus_9"),
                    "a failing next page");
            assertTrue(other.calls().stream().noneMatch(call -> call.path().endsWith("/expire")));
        }

        try (FakeStripeServer other = new FakeStripeServer()) {
            other.answer("GET", "/v1/checkout/sessions", sessions(false, "cs_1"))
                    .fail("POST", "/v1/checkout/sessions/cs_1/expire", 500, "{\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}");
            StripePaymentAdapter adapter = new StripePaymentAdapter(StripeClient.builder().setApiKey("sk_test_123")
                    .setApiBase(other.baseUrl()).setMaxNetworkRetries(0).build(), WEBHOOK_SECRET, TERMS, Clock.systemUTC());
            assertThrows(PaymentProviderUnavailableException.class, () -> adapter.expireOpenCheckoutSessions("cus_9"),
                    "a failing expiry");
        }
    }

    @Test
    void a_valid_signature_gives_the_event_type_and_customer() throws Exception {
        String payload = "{\"id\":\"evt_1\",\"object\":\"event\",\"type\":\"customer.subscription.updated\","
                + "\"data\":{\"object\":{\"id\":\"sub_1\",\"object\":\"subscription\",\"customer\":\"cus_9\"}}}";
        long now = System.currentTimeMillis() / 1000;

        BillingEvent event = adapter().parseWebhookEvent(payload, sign(payload, WEBHOOK_SECRET, now));

        assertEquals(new BillingEvent("customer.subscription.updated", "cus_9"), event);
    }

    @Test
    void an_event_without_a_customer_gives_a_null_customer() throws Exception {
        String payload = "{\"id\":\"evt_2\",\"object\":\"event\",\"type\":\"price.created\","
                + "\"data\":{\"object\":{\"id\":\"price_1\",\"object\":\"price\"}}}";

        BillingEvent event = adapter().parseWebhookEvent(payload,
                sign(payload, WEBHOOK_SECRET, System.currentTimeMillis() / 1000));

        assertEquals(new BillingEvent("price.created", null), event);
    }

    @Test
    void a_missing_or_wrong_signature_is_refused() throws Exception {
        String payload = "{\"id\":\"evt_1\",\"object\":\"event\",\"type\":\"customer.subscription.updated\","
                + "\"data\":{\"object\":{\"customer\":\"cus_9\"}}}";
        long now = System.currentTimeMillis() / 1000;
        StripePaymentAdapter adapter = adapter();

        assertThrows(InvalidWebhookSignatureException.class, () -> adapter.parseWebhookEvent(payload, null));
        assertThrows(InvalidWebhookSignatureException.class, () -> adapter.parseWebhookEvent(payload, ""));
        assertThrows(InvalidWebhookSignatureException.class, () -> adapter.parseWebhookEvent(payload, "garbage"));
        assertThrows(InvalidWebhookSignatureException.class,
                () -> adapter.parseWebhookEvent(payload, sign(payload, "whsec_other", now)));
        assertThrows(InvalidWebhookSignatureException.class,
                () -> adapter.parseWebhookEvent(payload + " ", sign(payload, WEBHOOK_SECRET, now)));
        assertThrows(InvalidWebhookSignatureException.class,
                () -> adapter.parseWebhookEvent(payload, sign(payload, WEBHOOK_SECRET, now - 3600)),
                "a replayed old signature is refused");
        assertThrows(InvalidWebhookSignatureException.class, () -> adapter.parseWebhookEvent(null, "t=1,v1=a"));
    }

    @Test
    void a_blank_webhook_secret_makes_the_webhook_unavailable() throws Exception {
        String payload = "{\"id\":\"evt_1\",\"object\":\"event\",\"type\":\"x\",\"data\":{\"object\":{}}}";

        assertThrows(PaymentProviderUnavailableException.class,
                () -> adapter("").parseWebhookEvent(payload, sign(payload, WEBHOOK_SECRET, System.currentTimeMillis() / 1000)));
    }

    @Test
    void a_stripe_error_becomes_unavailable() {
        stripe.fail("POST", "/v1/customers", 500, "{\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}")
                .fail("GET", "/v1/subscriptions", 401,
                        "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad key\"}}");
        StripePaymentAdapter adapter = adapter();

        assertThrows(PaymentProviderUnavailableException.class, () -> adapter.createCustomer(ACCOUNT, "a@b.c"));
        assertThrows(PaymentProviderUnavailableException.class, () -> adapter.findSubscriptions("cus_9"));
        assertThrows(PaymentProviderUnavailableException.class, () -> adapter.cancelNow("sub_missing"));
    }

}
