package fr.barlords.dndgateapi.domain.functional.service.adventure;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureFinderApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AdventureFinderService implements AdventureFinderApi {

    private final AdventurePersistenceSpi spi;

    private final CurrentAccountService currentAccountService;

    @Override
    public Page<Adventure> findVisibleTo(UUID accountId, PageRequest request) {
        return spi.findVisibleTo(accountId, request);
    }

    @Override
    public Optional<Adventure> findById(UUID id) {
        return spi.findById(id);
    }

    @Override
    public Optional<Adventure> findByIdVisibleTo(UUID accountId, UUID id) {
        return spi.findById(id)
                .filter(adventure -> adventure.isVisibleTo(accountId));
    }

    @Override
    public Optional<Adventure> findByInvitationCode(String code) {
        return spi.findByInvitationCode(code);
    }

    @Override
    public Page<Adventure> findWhereImMaster(UUID accountId, PageRequest request) {
        Account account = currentAccountService.getAccountById(accountId);

        return spi.findPageByCreatorId(account.getId(), request);
    }

    @Override
    public Page<Adventure> findWhereImPlayer(UUID accountId, PageRequest request) {
        Account account = currentAccountService.getAccountById(accountId);
        return spi.findPageByPlayerId(account.getId(), request);
    }

    @Override
    public Page<Adventure> findAvailableAdventures(UUID accountId, PageRequest request) {
        Account account = currentAccountService.getAccountById(accountId);

        return spi.findAvailableTo(account.getId(), request);
    }


}
