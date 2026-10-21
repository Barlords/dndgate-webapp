package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.bootstrap.config.RetryingTransaction;
import fr.barlords.dndgateapi.bootstrap.config.TransactionalAvailabilityVote;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.planning.AvailabilityCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.planning.AvailabilityFinderService;
import fr.barlords.dndgateapi.domain.functional.service.planning.AvailabilityUpdaterService;
import fr.barlords.dndgateapi.domain.port.in.planning.AvailabilityCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.planning.AvailabilityFinderApi;
import fr.barlords.dndgateapi.domain.port.in.planning.AvailabilityUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.AvailabilityPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.functional.service.planning.PlanningVisibilityVerifierService;
import fr.barlords.dndgateapi.domain.port.out.PlanningPersistenceSpi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class AvailabilityServiceConfiguration {
    @Bean
    public AvailabilityCreatorApi availabilityCreatorApi(AvailabilityUpdaterApi availabilityUpdaterApi) {
        return new AvailabilityCreatorService(availabilityUpdaterApi);
    }

    @Bean
    public AvailabilityFinderApi availabilityFinderApi(
            AvailabilityPersistenceSpi spi,
            PlanningPersistenceSpi planningSpi,
            AdventureAccessVerifierApi adventureAccessVerifierApi
    ) {
        return new AvailabilityFinderService(spi, planningSpi, adventureAccessVerifierApi);
    }

    @Bean
    public AvailabilityUpdaterApi availabilityUpdaterApi(
            AvailabilityPersistenceSpi spi,
            PlanningVisibilityVerifierService planningVisibilityVerifierService,
            RetryingTransaction transaction
    ) {
        return new TransactionalAvailabilityVote(
                new AvailabilityUpdaterService(spi, planningVisibilityVerifierService), transaction);
    }
}
