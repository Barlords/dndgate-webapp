package fr.barlords.dndgateapi.domain.functional.service.adventure;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AdventureCreatorService implements AdventureCreatorApi {

    private final AdventurePersistenceSpi spi;

    private final CurrentAccountService currentAccountService;

    private final EntitlementApi entitlementApi;

    @Override
    public Adventure create(UUID accountId, Adventure adventure) {
        Account owner = currentAccountService.getAccountById(accountId);

        entitlementApi.verifyAccountLimit(owner.getId(), PlanLimit.ACTIVE_ADVENTURES_AS_MASTER, spi.countActiveByOwnerId(owner.getId()));

        return spi.save(adventure.withOwnerId(owner.getId()));
    }

}
