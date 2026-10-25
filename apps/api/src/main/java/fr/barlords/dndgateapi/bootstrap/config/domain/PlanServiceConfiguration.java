package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.domain.functional.model.plan.PlanCatalog;
import fr.barlords.dndgateapi.domain.functional.service.plan.EntitlementService;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.port.out.SubscriptionPersistenceSpi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlanServiceConfiguration {

    @Bean
    public EntitlementApi entitlementApi(SubscriptionPersistenceSpi spi, PlanCatalog catalog) {
        return new EntitlementService(spi, catalog);
    }

}
