package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.planning.PlanningCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.planning.PlanningFinderService;
import fr.barlords.dndgateapi.domain.functional.service.planning.PlanningUpdaterService;
import fr.barlords.dndgateapi.domain.port.in.planning.PlanningCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.planning.PlanningFinderApi;
import fr.barlords.dndgateapi.domain.port.in.planning.PlanningUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.PlanningPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureFinderApi;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.functional.service.planning.PlanningVisibilityVerifierService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlanningServiceConfiguration {

    @Bean
    public PlanningCreatorApi planningCreatorApi(
            PlanningPersistenceSpi spi,
            AdventureOwnerVerifierService adventureOwnerVerifierService
    ) {
        return new PlanningCreatorService(spi, adventureOwnerVerifierService);
    }

    @Bean
    public PlanningFinderApi planningFinderApi(
            PlanningPersistenceSpi spi,
            AdventureFinderApi adventureFinderApi
    ) {
        return new PlanningFinderService(spi, adventureFinderApi);
    }

    @Bean
    public PlanningUpdaterApi planningUpdaterApi(
            PlanningPersistenceSpi spi,
            AdventureOwnerVerifierService adventureOwnerVerifierService
    ) {
        return new PlanningUpdaterService(spi, adventureOwnerVerifierService);
    }

    @Bean
    public PlanningVisibilityVerifierService planningVisibilityVerifierService(
            PlanningPersistenceSpi spi,
            AdventureFinderApi adventureFinderApi
    ) {
        return new PlanningVisibilityVerifierService(spi, adventureFinderApi);
    }
}