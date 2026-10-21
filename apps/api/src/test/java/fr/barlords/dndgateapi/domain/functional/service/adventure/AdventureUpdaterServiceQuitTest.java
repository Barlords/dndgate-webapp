package fr.barlords.dndgateapi.domain.functional.service.adventure;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.PlayerNotInAdventureException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.account.AccountFinderApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureFinderApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import io.vavr.control.Either;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdventureUpdaterServiceQuitTest {

    @Mock
    private AdventurePersistenceSpi spi;

    @Mock
    private AdventureFinderApi adventureFinderApi;

    @Mock
    private CurrentAccountService currentAccountService;

    @Mock
    private AdventureOwnerVerifierService adventureOwnerVerifierService;

    @Mock
    private AccountFinderApi accountFinderApi;

    @Mock
    private EmailingSpi emailingSpi;

    private AdventureUpdaterService service;

    private final Account leaver = Account.builder().pseudo("leaver").email("leaver@test.fr").build();

    private final Account stayer = Account.builder().pseudo("stayer").email("stayer@test.fr").build();

    @BeforeEach
    void setUp() {
        service = new AdventureUpdaterService(spi, adventureFinderApi, currentAccountService,
                adventureOwnerVerifierService, accountFinderApi, emailingSpi);
        when(currentAccountService.getAccountById(leaver.getId())).thenReturn(leaver);
    }

    private Adventure adventureWith(UUID... players) {
        return Adventure.builder().players(new ArrayList<>(List.of(players))).build();
    }

    @Test
    void a_player_who_leaves_is_removed_and_the_others_stay() {
        Adventure adventure = adventureWith(stayer.getId(), leaver.getId());
        when(adventureFinderApi.findById(adventure.getId())).thenReturn(Optional.of(adventure));
        when(spi.save(any(Adventure.class))).thenAnswer(i -> Either.right(i.getArguments()[0]));

        Adventure result = service.quitAdventure(adventure.getId(), leaver.getId());

        Assertions.assertEquals(List.of(stayer.getId()), result.getPlayers());
        ArgumentCaptor<Adventure> saved = ArgumentCaptor.forClass(Adventure.class);
        verify(spi).save(saved.capture());
        Assertions.assertEquals(List.of(stayer.getId()), saved.getValue().getPlayers());
    }

    @Test
    void the_list_read_from_the_database_is_not_modified_in_place() {
        Adventure adventure = adventureWith(stayer.getId(), leaver.getId());
        when(adventureFinderApi.findById(adventure.getId())).thenReturn(Optional.of(adventure));
        when(spi.save(any(Adventure.class))).thenAnswer(i -> Either.right(i.getArguments()[0]));

        service.quitAdventure(adventure.getId(), leaver.getId());

        Assertions.assertEquals(List.of(stayer.getId(), leaver.getId()), adventure.getPlayers());
    }

    @Test
    void someone_who_is_not_a_player_cannot_leave_and_nothing_is_saved() {
        Adventure adventure = adventureWith(stayer.getId());
        when(adventureFinderApi.findById(adventure.getId())).thenReturn(Optional.of(adventure));

        Assertions.assertThrows(PlayerNotInAdventureException.class,
                () -> service.quitAdventure(adventure.getId(), leaver.getId()));

        verify(spi, never()).save(any());
    }

    @Test
    void leaving_an_unknown_adventure_is_a_not_found() {
        UUID id = UUID.randomUUID();
        when(adventureFinderApi.findById(id)).thenReturn(Optional.empty());

        Assertions.assertThrows(AdventureNotFoundException.class, () -> service.quitAdventure(id, leaver.getId()));

        verify(spi, never()).save(any());
    }

    @Test
    void a_failed_save_is_reported_not_swallowed() {
        Adventure adventure = adventureWith(leaver.getId());
        when(adventureFinderApi.findById(adventure.getId())).thenReturn(Optional.of(adventure));
        when(spi.save(any(Adventure.class))).thenReturn(Either.left(null));

        Assertions.assertThrows(DataNotSaveException.class, () -> service.quitAdventure(adventure.getId(), leaver.getId()));
    }
}
