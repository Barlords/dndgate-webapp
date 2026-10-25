package fr.barlords.dndgateapi.domain.functional.service.adventure;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.PlayerNotInAdventureException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.account.AccountFinderApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureFinderApi;
import fr.barlords.dndgateapi.domain.port.out.AccessRevocationSpi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.functional.exception.DeletionNotConfirmedException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.PlanLimitReachedException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanDecider;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import org.mockito.InOrder;
import java.util.Map;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

    @Mock
    private AccessRevocationSpi accessRevocation;

    @Mock
    private GameCharacterPersistenceSpi gameCharacters;

    @Mock
    private MinioSpi minioSpi;

    @Mock
    private AuditSpi audit;

    @Mock
    private EntitlementApi entitlementApi;

    private AdventureUpdaterService service;

    private final Account leaver = Account.builder().pseudo("leaver").email("leaver@test.fr").build();

    private final Account stayer = Account.builder().pseudo("stayer").email("stayer@test.fr").build();

    @BeforeEach
    void setUp() {
        service = new AdventureUpdaterService(spi, adventureFinderApi, currentAccountService,
                adventureOwnerVerifierService, accountFinderApi, emailingSpi,
                org.mockito.Mockito.mock(fr.barlords.dndgateapi.domain.port.out.InvitationLogSpi.class), java.time.Clock.systemUTC(),
                new fr.barlords.dndgateapi.domain.functional.model.adventure.InvitationLimits(20, java.time.Duration.ofHours(1), java.time.Duration.ofHours(24)),
                accessRevocation, gameCharacters, minioSpi, audit, entitlementApi);
        when(currentAccountService.getAccountById(leaver.getId())).thenReturn(leaver);
    }

    private Adventure adventureWith(UUID... players) {
        return Adventure.builder().players(new ArrayList<>(List.of(players))).build();
    }

    @Test
    void a_player_who_leaves_is_removed_and_the_others_stay() {
        Adventure adventure = adventureWith(stayer.getId(), leaver.getId());
        when(adventureFinderApi.findById(adventure.getId())).thenReturn(Optional.of(adventure));
        when(spi.save(any(Adventure.class))).thenAnswer(i -> i.getArguments()[0]);

        Adventure result = service.quitAdventure(adventure.getId(), leaver.getId(), Map.of());

        Assertions.assertEquals(List.of(stayer.getId()), result.getPlayers());
        ArgumentCaptor<Adventure> saved = ArgumentCaptor.forClass(Adventure.class);
        verify(spi).save(saved.capture());
        Assertions.assertEquals(List.of(stayer.getId()), saved.getValue().getPlayers());
    }

    @Test
    void the_live_channels_of_the_player_who_leaves_are_cut_and_only_theirs() {
        Adventure adventure = adventureWith(stayer.getId(), leaver.getId());
        when(adventureFinderApi.findById(adventure.getId())).thenReturn(Optional.of(adventure));
        when(spi.save(any(Adventure.class))).thenAnswer(i -> i.getArguments()[0]);

        service.quitAdventure(adventure.getId(), leaver.getId(), Map.of());

        verify(accessRevocation).participationEnded(leaver.getId(), adventure.getId());
        verifyNoMoreInteractions(accessRevocation);
    }

    @Test
    void the_list_read_from_the_database_is_not_modified_in_place() {
        Adventure adventure = adventureWith(stayer.getId(), leaver.getId());
        when(adventureFinderApi.findById(adventure.getId())).thenReturn(Optional.of(adventure));
        when(spi.save(any(Adventure.class))).thenAnswer(i -> i.getArguments()[0]);

        service.quitAdventure(adventure.getId(), leaver.getId(), Map.of());

        Assertions.assertEquals(List.of(stayer.getId(), leaver.getId()), adventure.getPlayers());
    }

    @Test
    void someone_who_is_not_a_player_cannot_leave_and_nothing_is_saved() {
        Adventure adventure = adventureWith(stayer.getId());
        when(adventureFinderApi.findById(adventure.getId())).thenReturn(Optional.of(adventure));

        Assertions.assertThrows(PlayerNotInAdventureException.class,
                () -> service.quitAdventure(adventure.getId(), leaver.getId(), Map.of()));

        verify(spi, never()).save(any());
        verifyNoInteractions(accessRevocation);
    }

    @Test
    void leaving_an_unknown_adventure_is_a_not_found() {
        UUID id = UUID.randomUUID();
        when(adventureFinderApi.findById(id)).thenReturn(Optional.empty());

        Assertions.assertThrows(AdventureNotFoundException.class, () -> service.quitAdventure(id, leaver.getId(), Map.of()));

        verify(spi, never()).save(any());
    }

    @Test
    void a_failed_save_is_reported_not_swallowed() {
        Adventure adventure = adventureWith(leaver.getId());
        when(adventureFinderApi.findById(adventure.getId())).thenReturn(Optional.of(adventure));
        when(spi.save(any(Adventure.class))).thenThrow(new IllegalStateException("db down"));

        Assertions.assertThrows(IllegalStateException.class, () -> service.quitAdventure(adventure.getId(), leaver.getId(), Map.of()));
        verifyNoInteractions(accessRevocation);
    }

    // ---- the characters of the player ----

    private GameCharacter characterOf(Account owner, Adventure in, String name, String picture) {
        return GameCharacter.builder().owner(owner).adventure(in).name(name).picturePath(picture).active(true).build();
    }

    private Adventure joinedAdventure() {
        Adventure adventure = adventureWith(stayer.getId(), leaver.getId());
        when(adventureFinderApi.findById(adventure.getId())).thenReturn(Optional.of(adventure));
        lenient().when(spi.save(any(Adventure.class))).thenAnswer(i -> i.getArguments()[0]);
        return adventure;
    }

    @Test
    void quitting_moves_the_characters_to_the_reserve() {
        Adventure adventure = joinedAdventure();
        GameCharacter mine = characterOf(leaver, adventure, "Gandalf", "account/1/g.png");
        GameCharacter theirs = characterOf(stayer, adventure, "Frodo", null);
        when(gameCharacters.findByAdventureId(adventure.getId())).thenReturn(List.of(mine, theirs));
        when(gameCharacters.countByOwnerIdWithoutAdventure(leaver.getId())).thenReturn(0L);

        Adventure left = service.quitAdventure(adventure.getId(), leaver.getId(), Map.of());

        Assertions.assertEquals(List.of(stayer.getId()), left.getPlayers());
        ArgumentCaptor<GameCharacter> saved = ArgumentCaptor.forClass(GameCharacter.class);
        verify(gameCharacters).save(saved.capture());
        Assertions.assertEquals(mine.getId(), saved.getValue().getId());
        Assertions.assertNull(saved.getValue().getAdventure());
        Assertions.assertFalse(saved.getValue().isActive());
        verify(gameCharacters, never()).deleteById(any());
        verify(minioSpi, never()).deleteAfterCommit(any());
    }

    @Test
    void quitting_with_a_full_reserve_is_refused_and_keeps_the_seat() {
        Adventure adventure = joinedAdventure();
        when(gameCharacters.findByAdventureId(adventure.getId()))
                .thenReturn(List.of(characterOf(leaver, adventure, "A", null), characterOf(leaver, adventure, "B", null)));
        when(gameCharacters.countByOwnerIdWithoutAdventure(leaver.getId())).thenReturn(2L);
        // Two arrive in a reserve holding two: the plan allows 3, so the limit is met at used = 3
        doThrow(new PlanLimitReachedException(PlanLimit.CHARACTERS_WITHOUT_ADVENTURE, 3, "adventurer", "hero", PlanDecider.ACCOUNT))
                .when(entitlementApi).verifyAccountLimit(leaver.getId(), PlanLimit.CHARACTERS_WITHOUT_ADVENTURE, 3L);

        Assertions.assertThrows(PlanLimitReachedException.class,
                () -> service.quitAdventure(adventure.getId(), leaver.getId(), Map.of()));

        verify(spi, never()).save(any());
        verify(gameCharacters, never()).save(any());
        verify(gameCharacters, never()).deleteById(any());
        verifyNoInteractions(accessRevocation);
    }

    @Test
    void the_room_needed_in_the_reserve_is_what_is_there_plus_the_characters_that_arrive() {
        Adventure adventure = joinedAdventure();
        when(gameCharacters.findByAdventureId(adventure.getId()))
                .thenReturn(List.of(characterOf(leaver, adventure, "A", null), characterOf(leaver, adventure, "B", null)));
        when(gameCharacters.countByOwnerIdWithoutAdventure(leaver.getId())).thenReturn(5L);

        service.quitAdventure(adventure.getId(), leaver.getId(), Map.of());

        verify(entitlementApi).verifyAccountLimit(leaver.getId(), PlanLimit.CHARACTERS_WITHOUT_ADVENTURE, 6L);
    }

    @Test
    void quitting_with_confirmed_deletions_deletes_them_and_moves_the_rest() {
        Adventure adventure = joinedAdventure();
        GameCharacter deleted = characterOf(leaver, adventure, "Boromir", "account/1/b.png");
        GameCharacter kept = characterOf(leaver, adventure, "Gandalf", "account/1/g.png");
        when(gameCharacters.findByAdventureId(adventure.getId())).thenReturn(List.of(deleted, kept));
        when(gameCharacters.countByOwnerIdWithoutAdventure(leaver.getId())).thenReturn(0L);

        service.quitAdventure(adventure.getId(), leaver.getId(), Map.of(deleted.getId(), " Boromir "));

        InOrder order = inOrder(gameCharacters, audit, minioSpi);
        order.verify(gameCharacters).deleteById(deleted.getId());
        order.verify(audit).deletion("character", leaver.getId(), deleted.getId());
        order.verify(minioSpi).deleteAfterCommit("account/1/b.png");
        ArgumentCaptor<GameCharacter> moved = ArgumentCaptor.forClass(GameCharacter.class);
        verify(gameCharacters).save(moved.capture());
        Assertions.assertEquals(kept.getId(), moved.getValue().getId());
        verify(minioSpi, never()).deleteAfterCommit(eq("account/1/g.png"));
        // The deleted character does not need room in the reserve
        verify(entitlementApi).verifyAccountLimit(leaver.getId(), PlanLimit.CHARACTERS_WITHOUT_ADVENTURE, 0L);
    }

    @Test
    void deleting_every_character_needs_no_room_in_the_reserve() {
        Adventure adventure = joinedAdventure();
        GameCharacter only = characterOf(leaver, adventure, "Boromir", null);
        when(gameCharacters.findByAdventureId(adventure.getId())).thenReturn(List.of(only));

        service.quitAdventure(adventure.getId(), leaver.getId(), Map.of(only.getId(), "Boromir"));

        verifyNoInteractions(entitlementApi);
        verify(gameCharacters).deleteById(only.getId());
        verify(spi).save(any(Adventure.class));
    }

    @Test
    void a_wrong_name_in_a_deletion_refuses_the_whole_quit() {
        Adventure adventure = joinedAdventure();
        GameCharacter deleted = characterOf(leaver, adventure, "Boromir", "account/1/b.png");
        GameCharacter kept = characterOf(leaver, adventure, "Gandalf", null);
        when(gameCharacters.findByAdventureId(adventure.getId())).thenReturn(List.of(deleted, kept));

        Assertions.assertThrows(DeletionNotConfirmedException.class,
                () -> service.quitAdventure(adventure.getId(), leaver.getId(), Map.of(deleted.getId(), "Gimli")));

        verify(spi, never()).save(any());
        verify(gameCharacters, never()).save(any());
        verify(gameCharacters, never()).deleteById(any());
        verifyNoInteractions(accessRevocation, minioSpi, audit);
    }

    @Test
    void a_deletion_of_a_character_that_is_not_of_the_player_in_that_adventure_refuses_the_quit() {
        Adventure adventure = joinedAdventure();
        GameCharacter others = characterOf(stayer, adventure, "Frodo", null);
        GameCharacter elsewhere = characterOf(leaver, null, "Elsewhere", null);
        when(gameCharacters.findByAdventureId(adventure.getId())).thenReturn(List.of(others));

        Assertions.assertThrows(DeletionNotConfirmedException.class,
                () -> service.quitAdventure(adventure.getId(), leaver.getId(), Map.of(others.getId(), "Frodo")));
        Assertions.assertThrows(DeletionNotConfirmedException.class,
                () -> service.quitAdventure(adventure.getId(), leaver.getId(), Map.of(elsewhere.getId(), "Elsewhere")));

        verify(spi, never()).save(any());
        verify(gameCharacters, never()).deleteById(any());
    }
}
