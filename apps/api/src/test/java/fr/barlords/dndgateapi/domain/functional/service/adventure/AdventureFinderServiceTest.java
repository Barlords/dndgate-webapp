package fr.barlords.dndgateapi.domain.functional.service.adventure;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdventureFinderServiceTest {

    @InjectMocks
    private AdventureFinderService service;

    @Mock
    private AdventurePersistenceSpi spi;

    @Mock
    private CurrentAccountService currentAccountService;

    private final UUID ownerId = UUID.randomUUID();

    private final UUID playerId = UUID.randomUUID();

    private final UUID strangerId = UUID.randomUUID();

    private final Adventure publicAdventure = Adventure.builder()
            .ownerId(ownerId).visibility(AdventureVisibility.PUBLIC).build();

    private final Adventure privateAdventure = Adventure.builder()
            .ownerId(ownerId).visibility(AdventureVisibility.PRIVATE)
            .players(new ArrayList<>(List.of(playerId))).build();

    private final PageRequest request = new PageRequest(0, 20);

    private final Page<Adventure> page = new Page<>(List.of(publicAdventure), request, 1);

    @Test
    void the_visible_adventures_are_selected_by_the_storage_for_the_account() {
        when(spi.findVisibleTo(strangerId, request)).thenReturn(page);

        Assertions.assertSame(page, service.findVisibleTo(strangerId, request));
    }

    @Test
    void the_adventures_of_the_master_and_of_the_player_are_asked_for_that_account() {
        Account account = Account.builder().build();
        when(currentAccountService.getAccountById(ownerId)).thenReturn(account);
        when(spi.findPageByCreatorId(account.getId(), request)).thenReturn(page);
        when(spi.findPageByPlayerId(account.getId(), request)).thenReturn(page);

        Assertions.assertSame(page, service.findWhereImMaster(ownerId, request));
        Assertions.assertSame(page, service.findWhereImPlayer(ownerId, request));
    }

    @Test
    void the_available_adventures_are_asked_for_that_account() {
        Account account = Account.builder().build();
        when(currentAccountService.getAccountById(strangerId)).thenReturn(account);
        when(spi.findAvailableTo(account.getId(), request)).thenReturn(page);

        Assertions.assertSame(page, service.findAvailableAdventures(strangerId, request));
    }

    @Test
    void stranger_should_not_get_a_private_adventure_by_id() {
        when(spi.findById(privateAdventure.getId())).thenReturn(Optional.of(privateAdventure));

        Assertions.assertTrue(service.findByIdVisibleTo(strangerId, privateAdventure.getId()).isEmpty());
    }

    @Test
    void player_should_get_a_private_adventure_by_id() {
        when(spi.findById(privateAdventure.getId())).thenReturn(Optional.of(privateAdventure));

        Assertions.assertEquals(Optional.of(privateAdventure), service.findByIdVisibleTo(playerId, privateAdventure.getId()));
    }
}
