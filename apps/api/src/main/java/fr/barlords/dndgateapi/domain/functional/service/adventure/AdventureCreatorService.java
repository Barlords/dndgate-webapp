package fr.barlords.dndgateapi.domain.functional.service.adventure;

import fr.barlords.dndgateapi.domain.functional.exception.QuantityLimitExceededException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AdventureCreatorService implements AdventureCreatorApi {

    private final AdventurePersistenceSpi spi;

    private final CurrentAccountService currentAccountService;

    private final QuantityLimits limits;

    @Override
    public Adventure create(UUID accountId, Adventure adventure) {
        Account owner = currentAccountService.getAccountById(accountId);

        if (spi.countActiveByOwnerId(owner.getId()) >= limits.activeAdventuresPerMaster()) {
            throw QuantityLimitExceededException.activeAdventures(limits.activeAdventuresPerMaster());
        }

        return spi.save(adventure.withOwnerId(owner.getId()));
    }

}
