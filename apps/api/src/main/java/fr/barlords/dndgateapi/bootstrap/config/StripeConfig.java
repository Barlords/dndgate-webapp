package fr.barlords.dndgateapi.bootstrap.config;

import com.stripe.StripeClient;
import fr.barlords.dndgateapi.domain.port.out.PaymentProviderSpi;
import fr.barlords.dndgateapi.server.adapter.StripePaymentAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Clock;

@Slf4j
@Configuration
public class StripeConfig {

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 20_000;
    private static final int MAX_NETWORK_RETRIES = 2;

    /**
     * A blank secret key means billing is off: the adapter is built without a client and reports itself disabled.
     * Not in production: there, a blank secret key or webhook secret (a variable set but empty) stops the start.
     */
    @Bean
    public PaymentProviderSpi paymentProviderSpi(
            @Value("${stripe.secret-key:}") String secretKey,
            @Value("${stripe.webhook-secret:}") String webhookSecret,
            @Value("${stripe.api-base:}") String apiBase,
            @Value("${billing.checkout.terms-acceptance-message}") String termsAcceptanceMessage,
            Environment environment,
            Clock clock) {
        if (environment.matchesProfiles("prod")) {
            requireNotBlank("stripe.secret-key", secretKey);
            requireNotBlank("stripe.webhook-secret", webhookSecret);
        }
        if (secretKey.isBlank()) {
            log.warn("No Stripe secret key is configured: billing is disabled");
            return new StripePaymentAdapter(null, webhookSecret, termsAcceptanceMessage, clock);
        }
        StripeClient.StripeClientBuilder builder = StripeClient.builder()
                .setApiKey(secretKey)
                .setMaxNetworkRetries(MAX_NETWORK_RETRIES)
                .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
                .setReadTimeout(READ_TIMEOUT_MILLIS);
        if (!apiBase.isBlank()) {
            builder.setApiBase(apiBase);
        }
        return new StripePaymentAdapter(builder.build(), webhookSecret, termsAcceptanceMessage, clock);
    }

    private static void requireNotBlank(String property, String value) {
        if (value.isBlank()) {
            throw new IllegalStateException("The property " + property + " must not be blank in production");
        }
    }

}
