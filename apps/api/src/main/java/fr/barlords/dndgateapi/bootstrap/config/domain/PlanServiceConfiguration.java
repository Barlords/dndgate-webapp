package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.domain.functional.model.plan.BillingUrls;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanCatalog;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.plan.AdventureEntitlementsService;
import fr.barlords.dndgateapi.domain.functional.service.plan.BillingService;
import fr.barlords.dndgateapi.domain.functional.service.plan.EntitlementService;
import fr.barlords.dndgateapi.domain.functional.service.plan.SubscriptionSynchronizer;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.plan.AdventureEntitlementsApi;
import fr.barlords.dndgateapi.domain.port.in.plan.BillingApi;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.port.out.AdventureNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.HistoricalNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MagicItemPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.PaymentProviderSpi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.SubscriptionPersistenceSpi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

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

    /**
     * Not wrapped in a transactional decorator on purpose: a call to Stripe must not hold a database transaction
     * open. Each write is one statement of the subscription adapter.
     */
    @Bean
    public BillingApi billingApi(PaymentProviderSpi provider,
                                 SubscriptionPersistenceSpi subscriptions,
                                 EntitlementApi entitlementApi,
                                 PlanCatalog catalog,
                                 CurrentAccountService currentAccountService,
                                 AdventurePersistenceSpi adventures,
                                 GameCharacterPersistenceSpi gameCharacters,
                                 SpellBookPersistenceSpi spellBooks,
                                 MagicItemPersistenceSpi magicItems,
                                 @Value("${app.web-base-url}") String webBaseUrl,
                                 Clock clock) {
        return new BillingService(provider, subscriptions,
                new SubscriptionSynchronizer(provider, subscriptions, catalog, clock),
                entitlementApi, catalog, currentAccountService, adventures, gameCharacters, spellBooks, magicItems,
                BillingUrls.of(webBaseUrl), clock);
    }

}
