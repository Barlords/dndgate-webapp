package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.functional.service.historicnote.HistoricalNoteCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.historicnote.HistoricalNoteDeleterService;
import fr.barlords.dndgateapi.domain.functional.service.historicnote.HistoricalNoteFinderService;
import fr.barlords.dndgateapi.domain.functional.service.historicnote.HistoricalNoteUpdaterService;
import fr.barlords.dndgateapi.domain.port.in.historicalnote.HistoricalNoteCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.historicalnote.HistoricalNoteDeleterApi;
import fr.barlords.dndgateapi.domain.port.in.historicalnote.HistoricalNoteFinderApi;
import fr.barlords.dndgateapi.domain.port.in.historicalnote.HistoricalNoteUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.HistoricalNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HistoricalNoteServiceConfiguration {


    @Bean
    public HistoricalNoteCreatorApi historicalNoteCreatorApi(
            HistoricalNotePersistenceSpi spi,
            AdventureOwnerVerifierService adventureOwnerVerifierService,
            EntitlementApi entitlementApi
    ) {
        return new HistoricalNoteCreatorService(spi, adventureOwnerVerifierService, entitlementApi);
    }

    @Bean
    public HistoricalNoteFinderApi historicalNoteFinderApi(
            HistoricalNotePersistenceSpi spi,
            AdventureAccessVerifierApi adventureAccessVerifierApi
    ) {
        return new HistoricalNoteFinderService(spi, adventureAccessVerifierApi);
    }

    @Bean
    public HistoricalNoteUpdaterApi historicalNoteUpdaterApi(
            HistoricalNotePersistenceSpi spi,
            AdventureOwnerVerifierService adventureOwnerVerifierService
    ) {
        return new HistoricalNoteUpdaterService(spi, adventureOwnerVerifierService);
    }

    @Bean
    public HistoricalNoteDeleterApi historicalNoteDeleterApi(
            HistoricalNotePersistenceSpi spi,
            CurrentAccountService currentAccountService,
            AuditSpi audit
    ) {
        return new HistoricalNoteDeleterService(spi, currentAccountService, audit);
    }

}
