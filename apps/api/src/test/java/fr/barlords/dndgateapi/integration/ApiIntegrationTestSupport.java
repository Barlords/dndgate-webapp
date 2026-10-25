package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.port.out.PaymentProviderSpi;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The application of {@link IntegrationTestBase}, whose payment provider is a mock. */
abstract class ApiIntegrationTestSupport extends IntegrationTestBase {

    /**
     * Never Stripe in these tests. Unstubbed, the mock says billing is disabled (isEnabled false) and finds nothing,
     * like the application without a Stripe key; the billing tests stub what they need (reset after each test).
     */
    @MockitoBean
    PaymentProviderSpi paymentProviderSpi;
}
