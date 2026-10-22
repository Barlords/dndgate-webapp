package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.bootstrap.config.RetryingTransaction;
import fr.barlords.dndgateapi.bootstrap.config.TransactionalRatingUpdater;
import fr.barlords.dndgateapi.domain.functional.service.rating.RatingCreatorService;
import fr.barlords.dndgateapi.domain.functional.service.rating.RatingEligibilityService;
import fr.barlords.dndgateapi.domain.functional.service.rating.RatingFinderService;
import fr.barlords.dndgateapi.domain.functional.service.rating.RatingUpdaterService;
import fr.barlords.dndgateapi.domain.port.in.rating.RatingCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.rating.RatingFinderApi;
import fr.barlords.dndgateapi.domain.port.in.rating.RatingUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.RatingPersistenceSpi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RatingServiceConfiguration {

    @Bean
    public RatingEligibilityService ratingEligibilityService(
            AdventurePersistenceSpi adventureSpi
    ) {
        return new RatingEligibilityService(adventureSpi);
    }

    @Bean
    public RatingCreatorApi ratingCreatorApi(
            RatingPersistenceSpi spi,
            RatingEligibilityService ratingEligibilityService
    ) {
        return new RatingCreatorService(spi, ratingEligibilityService);
    }

    @Bean
    public RatingFinderApi ratingFinderApi(
            RatingPersistenceSpi spi
    ) {
        return new RatingFinderService(spi);
    }

    @Bean
    public RatingUpdaterApi ratingUpdaterApi(
            RatingPersistenceSpi spi,
            RatingEligibilityService ratingEligibilityService,
            RetryingTransaction transaction
    ) {
        return new TransactionalRatingUpdater(new RatingUpdaterService(spi, ratingEligibilityService), transaction);
    }

}
