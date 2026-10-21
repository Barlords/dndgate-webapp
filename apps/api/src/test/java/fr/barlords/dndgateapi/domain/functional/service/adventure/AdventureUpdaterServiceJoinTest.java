package fr.barlords.dndgateapi.domain.functional.service.adventure;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureFullException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotJoinableException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.PlayerNotInAdventureException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.PlayersAlreadyInAdventureException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdventureUpdaterServiceJoinTest {

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

    private final Account owner = Account.builder().pseudo("master").email("master@test.fr").build();

    private final Account candidate = Account.builder().pseudo("candidate").email("candidate@test.fr").build();

    @BeforeEach
    void setUp() {
        service = new AdventureUpdaterService(spi, adventureFinderApi, currentAccountService,
                adventureOwnerVerifierService, accountFinderApi, emailingSpi,
                org.mockito.Mockito.mock(fr.barlords.dndgateapi.domain.port.out.InvitationLogSpi.class), java.time.Clock.systemUTC(),
                new fr.barlords.dndgateapi.domain.functional.model.adventure.InvitationLimits(20, java.time.Duration.ofHours(1), java.time.Duration.ofHours(24)),
                org.mockito.Mockito.mock(fr.barlords.dndgateapi.domain.port.out.AccessRevocationSpi.class));
        lenient().when(currentAccountService.getAccountById(candidate.getId())).thenReturn(candidate);
    }

    private Adventure adventure(AdventureStatus status, int maxPlayer, UUID... players) {
        return Adventure.builder()
                .ownerId(owner.getId())
                .status(status)
                .visibility(AdventureVisibility.PUBLIC)
                .maxPlayer(maxPlayer)
                .players(new ArrayList<>(List.of(players)))
                .build();
    }

    private void saveReturnsWhatItReceives() {
        when(spi.save(any(Adventure.class))).thenAnswer(i -> Either.right(i.getArguments()[0]));
    }

    // ---- by id ----

    @Test
    void joining_by_id_an_adventure_that_is_not_visible_is_a_not_found() {
        UUID id = UUID.randomUUID();
        when(adventureFinderApi.findByIdVisibleTo(candidate.getId(), id)).thenReturn(Optional.empty());

        Assertions.assertThrows(AdventureNotFoundException.class, () -> service.joinAdventure(id, candidate.getId()));
        verify(spi, never()).save(any());
    }

    @Test
    void a_player_joins_an_open_adventure() {
        Adventure adventure = adventure(AdventureStatus.NOT_STARTED, 4);
        when(adventureFinderApi.findByIdVisibleTo(candidate.getId(), adventure.getId())).thenReturn(Optional.of(adventure));
        saveReturnsWhatItReceives();

        Adventure joined = service.joinAdventure(adventure.getId(), candidate.getId());

        Assertions.assertEquals(List.of(candidate.getId()), joined.getPlayers());
        Assertions.assertEquals(AdventureStatus.NOT_STARTED, joined.getStatus());
        Assertions.assertTrue(adventure.getPlayers().isEmpty(), "the loaded adventure must not be modified in place");
    }

    @Test
    void the_adventure_starts_and_becomes_private_when_the_last_seat_is_taken() {
        Adventure adventure = adventure(AdventureStatus.NOT_STARTED, 2, UUID.randomUUID());
        when(adventureFinderApi.findByIdVisibleTo(candidate.getId(), adventure.getId())).thenReturn(Optional.of(adventure));
        saveReturnsWhatItReceives();

        Adventure joined = service.joinAdventure(adventure.getId(), candidate.getId());

        Assertions.assertEquals(AdventureStatus.ONGOING, joined.getStatus());
        Assertions.assertEquals(AdventureVisibility.PRIVATE, joined.getVisibility());
    }

    // ---- rules shared with the invitation code ----

    @Test
    void a_full_adventure_refuses_new_players() {
        Adventure adventure = adventure(AdventureStatus.NOT_STARTED, 1, UUID.randomUUID());
        when(adventureFinderApi.findByInvitationCode("code")).thenReturn(Optional.of(adventure));

        Assertions.assertThrows(AdventureFullException.class, () -> service.joinAdventureByCode(candidate.getId(), "code"));
        verify(spi, never()).save(any());
    }

    @Test
    void a_started_adventure_refuses_new_players() {
        for (AdventureStatus status : List.of(AdventureStatus.ONGOING, AdventureStatus.FINISH, AdventureStatus.ABORT)) {
            Adventure adventure = adventure(status, 4);
            when(adventureFinderApi.findByInvitationCode("code")).thenReturn(Optional.of(adventure));

            Assertions.assertThrows(AdventureNotJoinableException.class,
                    () -> service.joinAdventureByCode(candidate.getId(), "code"), status.name());
        }
        verify(spi, never()).save(any());
    }

    @Test
    void a_player_cannot_join_twice() {
        Adventure adventure = adventure(AdventureStatus.NOT_STARTED, 4, candidate.getId());
        when(adventureFinderApi.findByInvitationCode("code")).thenReturn(Optional.of(adventure));

        Assertions.assertThrows(PlayersAlreadyInAdventureException.class,
                () -> service.joinAdventureByCode(candidate.getId(), "code"));
    }

    @Test
    void the_owner_cannot_join_its_own_adventure_as_a_player() {
        Adventure adventure = adventure(AdventureStatus.NOT_STARTED, 4);
        when(currentAccountService.getAccountById(owner.getId())).thenReturn(owner);
        when(adventureFinderApi.findByInvitationCode("code")).thenReturn(Optional.of(adventure));

        Assertions.assertThrows(PlayersAlreadyInAdventureException.class,
                () -> service.joinAdventureByCode(owner.getId(), "code"));
        verify(spi, never()).save(any());
    }

    @Test
    void an_unknown_invitation_code_is_a_not_found() {
        when(adventureFinderApi.findByInvitationCode("nope")).thenReturn(Optional.empty());

        Assertions.assertThrows(AdventureNotFoundException.class, () -> service.joinAdventureByCode(candidate.getId(), "nope"));
    }

    // ---- quitting ----

    @Test
    void a_player_can_quit_without_modifying_the_loaded_list() {
        UUID other = UUID.randomUUID();
        Adventure adventure = adventure(AdventureStatus.NOT_STARTED, 4, candidate.getId(), other);
        when(adventureFinderApi.findById(adventure.getId())).thenReturn(Optional.of(adventure));
        saveReturnsWhatItReceives();

        Adventure left = service.quitAdventure(adventure.getId(), candidate.getId());

        ArgumentCaptor<Adventure> saved = ArgumentCaptor.forClass(Adventure.class);
        verify(spi).save(saved.capture());
        Assertions.assertEquals(List.of(other), saved.getValue().getPlayers());
        Assertions.assertEquals(List.of(other), left.getPlayers());
        Assertions.assertEquals(2, adventure.getPlayers().size());
    }

    @Test
    void someone_who_is_not_a_player_cannot_quit() {
        Adventure adventure = adventure(AdventureStatus.NOT_STARTED, 4);
        when(adventureFinderApi.findById(adventure.getId())).thenReturn(Optional.of(adventure));

        Assertions.assertThrows(PlayerNotInAdventureException.class,
                () -> service.quitAdventure(adventure.getId(), candidate.getId()));
    }
}
