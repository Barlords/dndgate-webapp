package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.model.plan.CheckoutRequest;
import fr.barlords.dndgateapi.domain.port.out.PaymentProviderSpi;
import fr.barlords.dndgateapi.server.adapter.FakeStripeServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The configuration builds the adapter from the real application.properties (dev profile). */
class StripeConfigTest {

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(StripeConfig.class)
                .withBean(Clock.class, Clock::systemUTC)
                .withPropertyValues("spring.profiles.active=dev");
    }

    @Test
    void without_a_secret_key_billing_is_disabled() {
        runner().withPropertyValues("stripe.secret-key=", "stripe.webhook-secret=")
                .run(context -> assertFalse(context.getBean(PaymentProviderSpi.class).isEnabled()));
    }

    @Test
    void the_french_consent_message_reaches_stripe_intact_with_the_terms_link() {
        try (FakeStripeServer stripe = new FakeStripeServer()) {
            stripe.answer("GET", "/v1/prices",
                            "{\"object\":\"list\",\"url\":\"/v1/prices\",\"has_more\":false,"
                                    + "\"data\":[{\"id\":\"price_1\",\"object\":\"price\"}]}")
                    .answer("POST", "/v1/checkout/sessions",
                            "{\"id\":\"cs_1\",\"object\":\"checkout.session\",\"url\":\"https://checkout.example/cs_1\"}");

            runner().withPropertyValues("stripe.secret-key=sk_test_1", "stripe.webhook-secret=whsec_1",
                            "stripe.api-base=" + stripe.baseUrl(), "app.web-base-url=https://web.example")
                    .run(context -> {
                        PaymentProviderSpi provider = context.getBean(PaymentProviderSpi.class);
                        assertTrue(provider.isEnabled());
                        provider.createCheckoutSession(new CheckoutRequest(
                                "cus_1", UUID.randomUUID(), "hero_monthly", "https://ok", "https://ko"));
                    });

            assertEquals("J'accepte les [CGV](https://web.example/terms) et je demande l'accès immédiat au service : "
                            + "je renonce ainsi à mon droit de rétractation de 14 jours.",
                    stripe.only("POST", "/v1/checkout/sessions").form()
                            .get("custom_text[terms_of_service_acceptance][message]"));
        }
    }

    @Test
    void without_an_api_base_the_enabled_adapter_targets_the_real_stripe() {
        runner().withPropertyValues("stripe.secret-key=sk_test_1", "stripe.webhook-secret=whsec_1")
                .run(context -> assertTrue(context.getBean(PaymentProviderSpi.class).isEnabled()));
    }

    private ApplicationContextRunner production() {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(StripeConfig.class)
                .withBean(Clock.class, Clock::systemUTC)
                .withPropertyValues("spring.profiles.active=prod", "app.web-base-url=https://web.example");
    }

    @Test
    void in_production_a_blank_key_or_webhook_secret_stops_the_start() {
        production().withPropertyValues("stripe.secret-key=", "stripe.webhook-secret=whsec_1").run(context -> {
            assertNotNull(context.getStartupFailure(), "a production without billing does not start");
            assertTrue(rootCause(context.getStartupFailure()) instanceof IllegalStateException);
            assertTrue(rootCause(context.getStartupFailure()).getMessage().contains("stripe.secret-key"));
        });
        production().withPropertyValues("stripe.secret-key=sk_live_1", "stripe.webhook-secret= ").run(context -> {
            assertNotNull(context.getStartupFailure());
            assertTrue(rootCause(context.getStartupFailure()).getMessage().contains("stripe.webhook-secret"));
        });
        production().withPropertyValues("stripe.secret-key=sk_live_1", "stripe.webhook-secret=whsec_1")
                .run(context -> assertTrue(context.getBean(PaymentProviderSpi.class).isEnabled()));
    }

    @Test
    void outside_production_a_blank_webhook_secret_keeps_the_start() {
        runner().withPropertyValues("stripe.secret-key=sk_test_1", "stripe.webhook-secret=")
                .run(context -> assertTrue(context.getBean(PaymentProviderSpi.class).isEnabled()));
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
