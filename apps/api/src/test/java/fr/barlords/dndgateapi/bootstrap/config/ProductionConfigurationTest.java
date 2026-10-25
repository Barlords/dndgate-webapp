package fr.barlords.dndgateapi.bootstrap.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** What production must never serve is switched off in its own profile file. */
class ProductionConfigurationTest {

    private Properties production() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource("application-prod.properties").getInputStream()) {
            properties.load(in);
        }
        return properties;
    }

    @Test
    void the_openapi_description_is_not_served_in_production() throws IOException {
        // it is committed in contracts/openapi.yaml; the running API has no reason to describe itself to the internet
        Assertions.assertEquals("false", production().getProperty("springdoc.api-docs.enabled"));
    }

    @Test
    void the_stripe_keys_have_no_default_in_production() throws IOException {
        // no ":" default: the API refuses to start in production without the keys
        Assertions.assertEquals("${STRIPE_SECRET_KEY}", production().getProperty("stripe.secret-key"));
        Assertions.assertEquals("${STRIPE_WEBHOOK_SECRET}", production().getProperty("stripe.webhook-secret"));
    }

}
