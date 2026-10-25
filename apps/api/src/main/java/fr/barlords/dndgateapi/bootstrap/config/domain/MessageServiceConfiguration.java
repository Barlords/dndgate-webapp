package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.domain.functional.service.message.MessageCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.message.MessageFinderService;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.bootstrap.config.TransactionalMessageCreator;
import fr.barlords.dndgateapi.domain.port.in.message.MessageCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.port.in.message.MessageFinderApi;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class MessageServiceConfiguration {

    @Bean
    public MessageCreatorApi messageCreatorApi(
            MessagePersistenceSpi spi,
            AdventureAccessVerifierApi adventureAccessVerifierApi,
            EntitlementApi entitlementApi,
            Clock clock,
            QuantityLimits quantityLimits
    ) {
        return new TransactionalMessageCreator(
                new MessageCreatorService(spi, adventureAccessVerifierApi, entitlementApi, clock, quantityLimits));
    }

    @Bean
    public MessageFinderApi messageFinderApi(
            MessagePersistenceSpi spi,
            AdventureAccessVerifierApi adventureAccessVerifierApi
    ) {
        return new MessageFinderService(spi, adventureAccessVerifierApi);
    }

}
