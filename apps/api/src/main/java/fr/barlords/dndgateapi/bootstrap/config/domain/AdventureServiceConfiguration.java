package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.adventure.*;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureDeleterApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureFinderApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureUpdaterApi;
import fr.barlords.dndgateapi.domain.port.in.account.AccountFinderApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import fr.barlords.dndgateapi.bootstrap.config.TransactionalAdventureDeleter;
import fr.barlords.dndgateapi.domain.port.out.AvailabilityPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import fr.barlords.dndgateapi.domain.port.out.PlanningPersistenceSpi;
import fr.barlords.dndgateapi.domain.functional.model.adventure.InvitationLimits;
import fr.barlords.dndgateapi.domain.port.in.adventure.InvitationLogCleanerApi;
import fr.barlords.dndgateapi.domain.port.out.InvitationLogSpi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class AdventureServiceConfiguration {

    @Bean
    public AdventureOwnerVerifierService adventureOwnerVerifierService(
            AdventureFinderApi adventureFinderApi,
            CurrentAccountService currentAccountService
    ) {
        return new AdventureOwnerVerifierService(adventureFinderApi, currentAccountService);
    }

    @Bean
    public AdventureCreatorApi adventureCreatorApi(
            AdventurePersistenceSpi spi,
            CurrentAccountService currentAccountService
    ) {
        return new AdventureCreatorService(spi, currentAccountService);
    }

    @Bean
    public AdventureDeleterApi adventureDeleterApi(
            AdventurePersistenceSpi spi,
            AdventureOwnerVerifierService adventureOwnerVerifierService,
            GameCharacterPersistenceSpi gameCharacters,
            MessagePersistenceSpi messages,
            PlanningPersistenceSpi plannings,
            AvailabilityPersistenceSpi availabilities,
            MinioSpi minioSpi,
            AuditSpi audit
    ) {
        return new TransactionalAdventureDeleter(new AdventureDeleterService(
                spi, adventureOwnerVerifierService, gameCharacters, messages, plannings, availabilities, minioSpi, audit));
    }

    @Bean
    public AdventureAccessVerifierApi adventureAccessVerifierApi(AdventurePersistenceSpi spi) {
        return new AdventureAccessVerifierService(spi);
    }

    @Bean
    public AdventureFinderApi adventureFinderApi(
            AdventurePersistenceSpi spi,
            CurrentAccountService currentAccountService
    ) {
        return new AdventureFinderService(spi, currentAccountService);
    }

    @Bean
    public AdventureUpdaterApi adventureUpdaterApi(
            AdventurePersistenceSpi spi,
            AdventureFinderApi adventureFinderApi,
            CurrentAccountService currentAccountService,
            AdventureOwnerVerifierService adventureOwnerVerifierService,
            AccountFinderApi accountFinderApi,
            EmailingSpi emailingSpi,
            InvitationLogSpi invitationLog,
            Clock clock,
            InvitationLimits invitationLimits
    ) {
        return new AdventureUpdaterService(spi, adventureFinderApi, currentAccountService, adventureOwnerVerifierService,
                accountFinderApi, emailingSpi, invitationLog, clock, invitationLimits);
    }

    @Bean
    public InvitationLimits invitationLimits(
            @Value("${invitation.rate-limit.sender.max:20}") int senderMax,
            @Value("${invitation.rate-limit.sender.window:PT1H}") Duration senderWindow,
            @Value("${invitation.rate-limit.recipient.window:PT24H}") Duration recipientWindow
    ) {
        return new InvitationLimits(senderMax, senderWindow, recipientWindow);
    }

    @Bean
    public InvitationLogCleanerApi invitationLogCleanerApi(InvitationLogSpi invitationLog, Clock clock, InvitationLimits invitationLimits) {
        return new InvitationLogCleanerService(invitationLog, clock, invitationLimits);
    }

}
