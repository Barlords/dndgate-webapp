package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.gamecharacter.*;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureFinderApi;
import fr.barlords.dndgateapi.domain.port.in.gamecharacter.GameCharacterCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.gamecharacter.GameCharacterDeleterApi;
import fr.barlords.dndgateapi.domain.port.in.gamecharacter.GameCharacterFinderApi;
import fr.barlords.dndgateapi.domain.port.in.gamecharacter.GameCharacterUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GameCharacterServiceConfiguration {

    @Bean
    public GameCharacterOwnerVerifierService gameCharacterOwnerVerifierService(
            GameCharacterPersistenceSpi spi
    ) {
        return new GameCharacterOwnerVerifierService(spi);
    }

    @Bean
    public GameCharacterCreatorApi gameCharacterCreatorApi(
            GameCharacterPersistenceSpi spi,
            AdventureAccessVerifierApi adventureAccessVerifierApi,
            CurrentAccountService currentAccountService,
            MinioSpi minioSpi
    ) {
        return new GameCharacterCreatorService(spi, adventureAccessVerifierApi, currentAccountService, minioSpi);
    }

    @Bean
    public GameCharacterFinderApi gameCharacterFinderApi(
            GameCharacterPersistenceSpi spi,
            AdventureAccessVerifierApi adventureAccessVerifierApi,
            MinioSpi minioSpi
    ) {
        return new GameCharacterFinderService(spi, adventureAccessVerifierApi, minioSpi);
    }

    @Bean
    public GameCharacterUpdaterApi gameCharacterUpdaterApi(
            GameCharacterPersistenceSpi spi,
            GameCharacterOwnerVerifierService gameCharacterOwnerVerifierService
    ) {
        return new GameCharacterUpdaterService(spi, gameCharacterOwnerVerifierService);
    }

    @Bean
    public GameCharacterDeleterApi gameCharacterDeleterApi(
            GameCharacterPersistenceSpi spi,
            GameCharacterOwnerVerifierService gameCharacterOwnerVerifierService,
            MinioSpi minioSpi,
            AuditSpi audit
    ) {
        return new GameCharacterDeleterService(spi, gameCharacterOwnerVerifierService, minioSpi, audit);
    }

}
