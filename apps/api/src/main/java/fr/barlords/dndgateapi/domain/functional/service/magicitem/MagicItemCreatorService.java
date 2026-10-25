package fr.barlords.dndgateapi.domain.functional.service.magicitem;

import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.magicitem.MagicItemCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.MagicItemPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class MagicItemCreatorService implements MagicItemCreatorApi {

    private final MagicItemPersistenceSpi spi;

    private final CurrentAccountService currentAccountService;

    private final EntitlementApi entitlementApi;

    @Override
    public MagicItem create(UUID accountId, MagicItem magicItem) {
        Account account = currentAccountService.getAccountById(accountId);

        entitlementApi.verifyAccountLimit(account.getId(), PlanLimit.MAGIC_ITEMS, spi.countByOwnerId(account.getId()));

        return spi.save(magicItem.withOwner(account));
    }

}
