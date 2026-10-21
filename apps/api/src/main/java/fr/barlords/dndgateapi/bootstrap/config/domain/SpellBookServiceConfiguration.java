package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.*;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.spell.SpellDeleterService;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.spell.SpellWriterService;
import fr.barlords.dndgateapi.domain.port.in.spellbook.*;
import fr.barlords.dndgateapi.domain.port.in.spellbook.spell.SpellDeleterApi;
import fr.barlords.dndgateapi.domain.port.in.spellbook.spell.SpellWriterApi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpellBookServiceConfiguration {

    @Bean
    public SpellBookCreatorApi spellBookCreatorApi(
            SpellBookPersistenceSpi spi,
            CurrentAccountService currentAccountService
    ) {
        return new SpellBookCreatorService(spi, currentAccountService);
    }

    @Bean
    public SpellBookDeleterApi spellBookDeleterApi(
            SpellBookPersistenceSpi spi,
            SpellBookOwnerVerifierService spellBookOwnerVerifierService,
            AuditSpi audit
            ) {
        return new SpellBookDeleterService(spi, spellBookOwnerVerifierService, audit);
    }

    @Bean
    public SpellBookFinderApi spellBookFinderApi(
            SpellBookPersistenceSpi spi
    ) {
        return new SpellBookFinderService(spi);
    }

    @Bean
    public SpellBookUpdaterApi spellBookUpdaterApi(
            SpellBookPersistenceSpi spi,
            SpellBookOwnerVerifierService spellBookOwnerVerifierService
    ) {
        return new SpellBookUpdaterService(spi, spellBookOwnerVerifierService);
    }

    @Bean
    public SpellBookImporterApi spellBookImporterApi(
            SpellBookPersistenceSpi spi,
            CurrentAccountService currentAccountService
    ) {
        return new SpellBookImporterService(spi, currentAccountService);
    }

    @Bean
    public SpellBookOwnerVerifierService spellBookOwnerVerifierService(
            SpellBookPersistenceSpi spi
    ) {
        return new SpellBookOwnerVerifierService(spi);
    }

    @Bean
    public SpellWriterApi spellWriterApi(
            SpellBookPersistenceSpi spi,
            SpellBookOwnerVerifierService spellBookOwnerVerifierService
    ) {
        return new SpellWriterService(spi, spellBookOwnerVerifierService);
    }

    @Bean
    public SpellDeleterApi spellDeleterApi(
            SpellBookPersistenceSpi spi,
            SpellBookOwnerVerifierService spellBookOwnerVerifierService
    ) {
        return new SpellDeleterService(spi, spellBookOwnerVerifierService);
    }

}
