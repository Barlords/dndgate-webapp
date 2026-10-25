package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.bootstrap.config.RetryingTransaction;
import fr.barlords.dndgateapi.bootstrap.config.TransactionalSpellDeleter;
import fr.barlords.dndgateapi.bootstrap.config.TransactionalSpellWriter;
import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.*;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.spell.SpellDeleterService;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.spell.SpellWriterService;
import fr.barlords.dndgateapi.domain.port.in.spellbook.*;
import fr.barlords.dndgateapi.domain.port.in.spellbook.spell.SpellDeleterApi;
import fr.barlords.dndgateapi.domain.port.in.spellbook.spell.SpellWriterApi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpellBookServiceConfiguration {

    @Bean
    public SpellBookCreatorApi spellBookCreatorApi(
            SpellBookPersistenceSpi spi,
            CurrentAccountService currentAccountService,
            EntitlementApi entitlementApi
    ) {
        return new SpellBookCreatorService(spi, currentAccountService, entitlementApi);
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
            CurrentAccountService currentAccountService,
            QuantityLimits quantityLimits,
            EntitlementApi entitlementApi
    ) {
        return new SpellBookImporterService(spi, currentAccountService, quantityLimits, entitlementApi);
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
            SpellBookOwnerVerifierService spellBookOwnerVerifierService,
            QuantityLimits quantityLimits,
            RetryingTransaction transaction
    ) {
        return new TransactionalSpellWriter(new SpellWriterService(spi, spellBookOwnerVerifierService, quantityLimits), transaction);
    }

    @Bean
    public SpellDeleterApi spellDeleterApi(
            SpellBookPersistenceSpi spi,
            SpellBookOwnerVerifierService spellBookOwnerVerifierService,
            RetryingTransaction transaction
    ) {
        return new TransactionalSpellDeleter(new SpellDeleterService(spi, spellBookOwnerVerifierService), transaction);
    }

}
