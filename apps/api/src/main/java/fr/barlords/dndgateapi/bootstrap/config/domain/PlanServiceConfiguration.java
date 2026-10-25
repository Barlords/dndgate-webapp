package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.domain.functional.model.plan.PlanCatalog;
import fr.barlords.dndgateapi.domain.functional.service.plan.AdventureEntitlementsService;
import fr.barlords.dndgateapi.domain.functional.service.plan.EntitlementService;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.plan.AdventureEntitlementsApi;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.port.out.AdventureNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.HistoricalNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.SubscriptionPersistenceSpi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlanServiceConfiguration {

    @Bean
    public EntitlementApi entitlementApi(SubscriptionPersistenceSpi spi, PlanCatalog catalog) {
        return new EntitlementService(spi, catalog);
    }

    @Bean
    public AdventureEntitlementsApi adventureEntitlementsApi(AdventureAccessVerifierApi adventureAccessVerifierApi,
                                                             EntitlementApi entitlementApi,
                                                             GameCharacterPersistenceSpi gameCharacters,
                                                             MessagePersistenceSpi messages,
                                                             AdventureNotePersistenceSpi adventureNotes,
                                                             HistoricalNotePersistenceSpi historicalNotes) {
        return new AdventureEntitlementsService(adventureAccessVerifierApi, entitlementApi, gameCharacters, messages,
                adventureNotes, historicalNotes);
    }

}
