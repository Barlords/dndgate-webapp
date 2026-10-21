package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.functional.service.adventurenote.AdventureNoteCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.adventurenote.AdventureNoteDeleterService;
import fr.barlords.dndgateapi.domain.functional.service.adventurenote.AdventureNoteFinderService;
import fr.barlords.dndgateapi.domain.functional.service.adventurenote.AdventureNoteUpdaterService;
import fr.barlords.dndgateapi.domain.port.in.adventurenote.AdventureNoteCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.adventurenote.AdventureNoteDeleterApi;
import fr.barlords.dndgateapi.domain.port.in.adventurenote.AdventureNoteFinderApi;
import fr.barlords.dndgateapi.domain.port.in.adventurenote.AdventureNoteUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.AdventureNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdventureNoteServiceConfiguration {

    @Bean
    public AdventureNoteCreatorApi adventureNoteCreatorApi(
            AdventureNotePersistenceSpi spi,
            AdventureOwnerVerifierService adventureOwnerVerifierService,
            QuantityLimits quantityLimits
    ) {
        return new AdventureNoteCreatorService(spi, adventureOwnerVerifierService, quantityLimits);
    }

    @Bean
    public AdventureNoteFinderApi adventureNoteFinderApi(
            AdventureNotePersistenceSpi spi,
            AdventureAccessVerifierApi adventureAccessVerifierApi
    ) {
        return new AdventureNoteFinderService(spi, adventureAccessVerifierApi);
    }

    @Bean
    public AdventureNoteUpdaterApi adventureNoteUpdaterApi(
            AdventureNotePersistenceSpi spi,
            AdventureOwnerVerifierService adventureOwnerVerifierService
    ) {
        return new AdventureNoteUpdaterService(spi, adventureOwnerVerifierService);
    }

    @Bean
    public AdventureNoteDeleterApi adventureNoteDeleterApi(
            AdventureNotePersistenceSpi spi,
            CurrentAccountService currentAccountService,
            AuditSpi audit
    ) {
        return new AdventureNoteDeleterService(spi, currentAccountService, audit);
    }

}
