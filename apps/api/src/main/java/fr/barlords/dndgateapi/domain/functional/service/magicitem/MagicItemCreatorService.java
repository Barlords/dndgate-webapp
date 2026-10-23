package fr.barlords.dndgateapi.domain.functional.service.magicitem;

import fr.barlords.dndgateapi.domain.functional.exception.QuantityLimitExceededException;
import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItem;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.magicitem.MagicItemCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.MagicItemPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class MagicItemCreatorService implements MagicItemCreatorApi {

    private final MagicItemPersistenceSpi spi;

    private final CurrentAccountService currentAccountService;

    private final QuantityLimits limits;

    @Override
    public MagicItem create(UUID accountId, MagicItem magicItem) {
        Account account = currentAccountService.getAccountById(accountId);

        if (spi.countByOwnerId(account.getId()) >= limits.magicItemsPerAccount()) {
            throw QuantityLimitExceededException.magicItems(limits.magicItemsPerAccount());
        }

        return spi.save(magicItem.withOwner(account));
    }

}
