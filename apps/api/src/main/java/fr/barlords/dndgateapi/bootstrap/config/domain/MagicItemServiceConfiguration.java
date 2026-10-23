package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.magicitem.*;
import fr.barlords.dndgateapi.domain.port.in.magicitem.MagicItemCreatorApi;
import fr.barlords.dndgateapi.domain.port.in.magicitem.MagicItemDeleterApi;
import fr.barlords.dndgateapi.domain.port.in.magicitem.MagicItemFinderApi;
import fr.barlords.dndgateapi.domain.port.in.magicitem.MagicItemUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.port.out.MagicItemPersistenceSpi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MagicItemServiceConfiguration {

    @Bean
    public MagicItemOwnerVerifierService magicItemOwnerVerifierService(
            MagicItemPersistenceSpi spi
    ) {
        return new MagicItemOwnerVerifierService(spi);
    }

    @Bean
    public MagicItemCreatorApi magicItemCreatorApi(
            MagicItemPersistenceSpi spi,
            CurrentAccountService currentAccountService,
            QuantityLimits quantityLimits
    ) {
        return new MagicItemCreatorService(spi, currentAccountService, quantityLimits);
    }

    @Bean
    public MagicItemFinderApi magicItemFinderApi(
            MagicItemPersistenceSpi spi
    ) {
        return new MagicItemFinderService(spi);
    }

    @Bean
    public MagicItemUpdaterApi magicItemUpdaterApi(
            MagicItemPersistenceSpi spi,
            MagicItemOwnerVerifierService magicItemOwnerVerifierService
    ) {
        return new MagicItemUpdaterService(spi, magicItemOwnerVerifierService);
    }

    @Bean
    public MagicItemDeleterApi magicItemDeleterApi(
            MagicItemPersistenceSpi spi,
            MagicItemOwnerVerifierService magicItemOwnerVerifierService,
            AuditSpi audit
    ) {
        return new MagicItemDeleterService(spi, magicItemOwnerVerifierService, audit);
    }

}
