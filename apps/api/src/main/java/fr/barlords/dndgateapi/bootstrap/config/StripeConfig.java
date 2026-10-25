package fr.barlords.dndgateapi.bootstrap.config;

import com.stripe.StripeClient;
import fr.barlords.dndgateapi.domain.port.out.PaymentProviderSpi;
import fr.barlords.dndgateapi.server.adapter.StripePaymentAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Slf4j
@Configuration
public class StripeConfig {

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 20_000;

    /** A blank secret key means billing is off: the adapter is built without a client and reports itself disabled. */
    @Bean
    public PaymentProviderSpi paymentProviderSpi(
            @Value("${stripe.secret-key:}") String secretKey,
            @Value("${stripe.webhook-secret:}") String webhookSecret,
            @Value("${stripe.api-base:}") String apiBase,
            @Value("${billing.checkout.terms-acceptance-message}") String termsAcceptanceMessage,
            Clock clock) {
        if (secretKey.isBlank()) {
            log.warn("No Stripe secret key is configured: billing is disabled");
            return new StripePaymentAdapter(null, webhookSecret, termsAcceptanceMessage, clock);
        }
        StripeClient.StripeClientBuilder builder = StripeClient.builder()
                .setApiKey(secretKey)
                .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
                .setReadTimeout(READ_TIMEOUT_MILLIS);
        if (!apiBase.isBlank()) {
            builder.setApiBase(apiBase);
        }
        return new StripePaymentAdapter(builder.build(), webhookSecret, termsAcceptanceMessage, clock);
    }

}
