package fr.barlords.dndgateapi.domain.functional.service.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureAccessDeniedException;
import fr.barlords.dndgateapi.domain.functional.exception.gamecharacter.GameCharacterOwnerException;
import fr.barlords.dndgateapi.domain.functional.exception.gamecharacter.GameCharacterPlacementException;
import fr.barlords.dndgateapi.domain.functional.exception.plan.PlanLimitReachedException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanDecider;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameCharacterMoverServiceTest {

    @Mock
    private GameCharacterPersistenceSpi spi;

    @Mock
    private AdventureAccessVerifierApi adventureAccessVerifierApi;

    @Mock
    private EntitlementApi entitlement;

    private GameCharacterMoverService service;

    private final Account player = Account.builder().pseudo("player").email("player@test.fr").build();

    private final Account master = Account.builder().pseudo("master").email("master@test.fr").build();

    private final Adventure adventure = Adventure.builder().name("Quest").ownerId(master.getId())
            .players(List.of(player.getId())).build();

    @BeforeEach
    void setUp() {
        service = new GameCharacterMoverService(spi, new GameCharacterOwnerVerifierService(spi), adventureAccessVerifierApi, entitlement);
    }

    private GameCharacter character(Adventure in, boolean active) {
        GameCharacter character = GameCharacter.builder().name("Gandalf").owner(player).adventure(in).active(active).build();
        when(spi.findById(character.getId())).thenReturn(Optional.of(character));
        return character;
    }

    private void saveReturnsItsArgument() {
        when(spi.save(any(GameCharacter.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    @Test
    void moving_to_a_full_reserve_is_refused_and_changes_nothing() {
        GameCharacter inAdventure = character(adventure, true);
        when(spi.countByOwnerIdWithoutAdventure(player.getId())).thenReturn(3L);
        doThrow(new PlanLimitReachedException(PlanLimit.CHARACTERS_WITHOUT_ADVENTURE, 3, "adventurer", "hero", PlanDecider.ACCOUNT))
                .when(entitlement).verifyAccountLimit(player.getId(), PlanLimit.CHARACTERS_WITHOUT_ADVENTURE, 3L);

        PlanLimitReachedException refused = Assertions.assertThrows(PlanLimitReachedException.class,
                () -> service.moveToReserve(player.getId(), inAdventure.getId()));

        Assertions.assertEquals(PlanLimit.CHARACTERS_WITHOUT_ADVENTURE, refused.getLimit());
        verify(spi, never()).save(any());
    }

    @Test
    void moving_to_the_reserve_frees_the_place_and_deactivates() {
        GameCharacter inAdventure = character(adventure, true);
        saveReturnsItsArgument();

        GameCharacter moved = service.moveToReserve(player.getId(), inAdventure.getId());

        Assertions.assertNull(moved.getAdventure());
        Assertions.assertFalse(moved.isActive());
        verify(entitlement).verifyAccountLimit(player.getId(), PlanLimit.CHARACTERS_WITHOUT_ADVENTURE, 0L);
    }

    @Test
    void a_character_already_in_the_reserve_stays_there_without_asking_for_a_place() {
        GameCharacter inReserve = character(null, false);

        Assertions.assertSame(inReserve, service.moveToReserve(player.getId(), inReserve.getId()));

        verify(entitlement, never()).verifyAccountLimit(any(), any(), org.mockito.ArgumentMatchers.anyLong());
        verify(spi, never()).save(any());
    }

    @Test
    void moving_into_an_adventure_respects_the_master_plan() {
        GameCharacter inReserve = character(null, false);
        when(adventureAccessVerifierApi.verifyParticipant(player.getId(), adventure.getId())).thenReturn(adventure);
        when(spi.countByOwnerIdAndAdventureId(player.getId(), adventure.getId())).thenReturn(1L);
        doThrow(new PlanLimitReachedException(PlanLimit.CHARACTERS_PER_PLAYER_AND_ADVENTURE, 1, "adventurer", "dungeon-master", PlanDecider.MASTER))
                .when(entitlement).verifyAdventureLimit(adventure, PlanLimit.CHARACTERS_PER_PLAYER_AND_ADVENTURE, 1L);

        Assertions.assertThrows(PlanLimitReachedException.class,
                () -> service.moveToAdventure(player.getId(), inReserve.getId(), adventure.getId()));

        verify(spi, never()).save(any());
    }

    @Test
    void the_first_character_of_a_player_becomes_active() {
        GameCharacter inReserve = character(null, false);
        when(adventureAccessVerifierApi.verifyParticipant(player.getId(), adventure.getId())).thenReturn(adventure);
        when(spi.findActive(player.getId(), adventure.getId())).thenReturn(Optional.empty());
        saveReturnsItsArgument();

        GameCharacter moved = service.moveToAdventure(player.getId(), inReserve.getId(), adventure.getId());

        Assertions.assertEquals(adventure, moved.getAdventure());
        Assertions.assertTrue(moved.isActive());
    }

    @Test
    void a_further_character_joins_the_adventure_without_taking_the_active_place() {
        GameCharacter inReserve = character(null, false);
        GameCharacter alreadyActive = GameCharacter.builder().owner(player).adventure(adventure).active(true).build();
        when(adventureAccessVerifierApi.verifyParticipant(player.getId(), adventure.getId())).thenReturn(adventure);
        when(spi.findActive(player.getId(), adventure.getId())).thenReturn(Optional.of(alreadyActive));
        saveReturnsItsArgument();

        GameCharacter moved = service.moveToAdventure(player.getId(), inReserve.getId(), adventure.getId());

        Assertions.assertFalse(moved.isActive());
    }

    @Test
    void the_master_does_not_move_a_character_into_their_own_adventure() {
        GameCharacter masterCharacter = GameCharacter.builder().owner(master).build();
        when(spi.findById(masterCharacter.getId())).thenReturn(Optional.of(masterCharacter));
        when(adventureAccessVerifierApi.verifyParticipant(master.getId(), adventure.getId())).thenReturn(adventure);

        Assertions.assertThrows(AdventureAccessDeniedException.class,
                () -> service.moveToAdventure(master.getId(), masterCharacter.getId(), adventure.getId()));

        verify(spi, never()).save(any());
    }

    @Test
    void a_character_that_is_already_in_an_adventure_cannot_be_moved_into_another() {
        GameCharacter inAdventure = character(adventure, true);

        Assertions.assertThrows(GameCharacterPlacementException.class,
                () -> service.moveToAdventure(player.getId(), inAdventure.getId(), adventure.getId()));

        verify(spi, never()).save(any());
    }

    @Test
    void activating_a_character_deactivates_the_previous_one() {
        GameCharacter previous = GameCharacter.builder().owner(player).adventure(adventure).active(true).build();
        GameCharacter chosen = character(adventure, false);
        when(spi.findActive(player.getId(), adventure.getId())).thenReturn(Optional.of(previous));
        saveReturnsItsArgument();

        GameCharacter activated = service.activate(player.getId(), adventure.getId(), chosen.getId());

        Assertions.assertTrue(activated.isActive());
        InOrder order = inOrder(spi);
        order.verify(spi).save(previous.withActive(false));
        order.verify(spi).save(chosen.withActive(true));
    }

    @Test
    void activating_the_active_character_changes_nothing() {
        GameCharacter active = character(adventure, true);

        Assertions.assertSame(active, service.activate(player.getId(), adventure.getId(), active.getId()));

        verify(spi, never()).save(any());
    }

    @Test
    void a_character_is_activated_only_in_its_own_adventure() {
        GameCharacter elsewhere = character(Adventure.builder().name("Other").build(), false);
        GameCharacter inReserve = character(null, false);

        Assertions.assertThrows(GameCharacterPlacementException.class,
                () -> service.activate(player.getId(), adventure.getId(), elsewhere.getId()));
        Assertions.assertThrows(GameCharacterPlacementException.class,
                () -> service.activate(player.getId(), adventure.getId(), inReserve.getId()));

        verify(spi, never()).save(any());
    }

    @Test
    void only_the_owner_moves_or_activates() {
        GameCharacter characterOfPlayer = character(adventure, false);
        Account stranger = Account.builder().pseudo("stranger").build();

        Assertions.assertThrows(GameCharacterOwnerException.class, () -> service.moveToReserve(stranger.getId(), characterOfPlayer.getId()));
        Assertions.assertThrows(GameCharacterOwnerException.class,
                () -> service.moveToAdventure(stranger.getId(), characterOfPlayer.getId(), adventure.getId()));
        Assertions.assertThrows(GameCharacterOwnerException.class,
                () -> service.activate(stranger.getId(), adventure.getId(), characterOfPlayer.getId()));

        verify(spi, never()).save(any());
    }

}
