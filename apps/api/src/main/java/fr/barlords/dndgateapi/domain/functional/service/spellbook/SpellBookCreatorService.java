package fr.barlords.dndgateapi.domain.functional.service.spellbook;

import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.spellbook.SpellBookCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class SpellBookCreatorService implements SpellBookCreatorApi {

    private final SpellBookPersistenceSpi spi;

    private final CurrentAccountService currentAccountService;

    private final EntitlementApi entitlementApi;

    @Override
    public SpellBook create(UUID accountId, SpellBook spellBook) {
        Account account = currentAccountService.getAccountById(accountId);

        entitlementApi.verifyAccountLimit(account.getId(), PlanLimit.SPELL_BOOKS, spi.countByOwnerId(account.getId()));

        return spi.save(spellBook.withOwner(account));
    }

}
