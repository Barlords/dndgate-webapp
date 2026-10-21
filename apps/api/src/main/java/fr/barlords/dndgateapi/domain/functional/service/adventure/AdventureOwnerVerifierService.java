package fr.barlords.dndgateapi.domain.functional.service.adventure;


import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureOwnerException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureFinderApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AdventureOwnerVerifierService {
    private final AdventureFinderApi adventureFinderApi;
    private final CurrentAccountService currentAccountService;
    public Adventure verify(UUID accountId, UUID adventureId) {
        Account account = currentAccountService.getAccountById(accountId);
        Adventure adventure = adventureFinderApi.findById(adventureId)
                .orElseThrow(AdventureNotFoundException::new);
        if(!adventure.isMyOwner(account)) {
            throw new AdventureOwnerException();
        }

        return adventure;
    }
}
