package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.domain.functional.service.message.MessageCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.message.MessageFinderService;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.message.MessageCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.message.MessageFinderApi;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class MessageServiceConfiguration {

    @Bean
    public MessageCreatorApi messageCreatorApi(
            MessagePersistenceSpi spi,
            AdventureAccessVerifierApi adventureAccessVerifierApi,
            Clock clock
    ) {
        return new MessageCreatorService(spi, adventureAccessVerifierApi, clock);
    }

    @Bean
    public MessageFinderApi messageFinderApi(
            MessagePersistenceSpi spi,
            AdventureAccessVerifierApi adventureAccessVerifierApi
    ) {
        return new MessageFinderService(spi, adventureAccessVerifierApi);
    }

}
