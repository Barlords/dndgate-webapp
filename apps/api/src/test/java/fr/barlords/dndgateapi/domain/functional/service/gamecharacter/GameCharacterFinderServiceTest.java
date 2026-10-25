package fr.barlords.dndgateapi.domain.functional.service.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureAccessDeniedException;
import fr.barlords.dndgateapi.domain.functional.exception.gamecharacter.GameCharacterNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.gamecharacter.GameCharacterOwnerException;
import fr.barlords.dndgateapi.domain.functional.model.CharacterVisibility;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Who reads a character: its owner, and the participants of its adventure when it has one. */
@ExtendWith(MockitoExtension.class)
class GameCharacterFinderServiceTest {

    @Mock
    private GameCharacterPersistenceSpi spi;

    @Mock
    private AdventureAccessVerifierApi adventureAccessVerifierApi;

    @Mock
    private MinioSpi minioSpi;

    private GameCharacterFinderService service;

    private final Account owner = Account.builder().pseudo("owner").email("owner@test.fr").build();

    private final Account other = Account.builder().pseudo("other").email("other@test.fr").build();

    private final Adventure adventure = Adventure.builder().name("Quest").build();

    @BeforeEach
    void setUp() {
        service = new GameCharacterFinderService(spi, adventureAccessVerifierApi, minioSpi);
    }

    private GameCharacter stored(Adventure of) {
        GameCharacter character = GameCharacter.builder().owner(owner).adventure(of).name("Gandalf").build();
        when(spi.findById(character.getId())).thenReturn(Optional.of(character));
        return character;
    }

    @Test
    void the_owner_reads_a_character_outside_any_adventure() {
        GameCharacter alone = stored(null);

        Assertions.assertEquals(Optional.of(alone), service.findById(owner.getId(), alone.getId()));
        verify(adventureAccessVerifierApi, never()).verifyParticipant(any(), any());
    }

    @Test
    void nobody_else_reads_a_character_outside_any_adventure() {
        GameCharacter alone = stored(null);

        Assertions.assertThrows(GameCharacterOwnerException.class, () -> service.findById(other.getId(), alone.getId()));
        verify(adventureAccessVerifierApi, never()).verifyParticipant(any(), any());
    }

    @Test
    void a_participant_of_its_adventure_reads_a_character() {
        GameCharacter played = stored(adventure);
        when(adventureAccessVerifierApi.verifyParticipant(other.getId(), adventure.getId())).thenReturn(adventure);

        Assertions.assertEquals(Optional.of(played), service.findById(other.getId(), played.getId()));
    }

    @Test
    void someone_outside_its_adventure_does_not_read_a_character() {
        GameCharacter played = stored(adventure);
        doThrow(new AdventureAccessDeniedException()).when(adventureAccessVerifierApi)
                .verifyParticipant(other.getId(), adventure.getId());

        Assertions.assertThrows(AdventureAccessDeniedException.class, () -> service.findById(other.getId(), played.getId()));
    }

    @Test
    void the_picture_of_a_character_outside_any_adventure_is_refused_to_someone_else() throws Exception {
        GameCharacter alone = stored(null);

        Assertions.assertThrows(GameCharacterOwnerException.class, () -> service.findPicture(other.getId(), alone.getId()));
        verify(minioSpi, never()).downloadFile(any());
    }

    @Test
    void a_character_without_picture_has_no_picture_to_serve() throws Exception {
        GameCharacter alone = stored(null);

        Assertions.assertThrows(GameCharacterNotFoundException.class, () -> service.findPicture(owner.getId(), alone.getId()));
        verify(minioSpi, never()).downloadFile(any());
    }

    // ---- non-player characters of the game master ----

    private final Account master = Account.builder().pseudo("master").email("master@test.fr").build();

    private final Adventure mastered = Adventure.builder().name("Quest").ownerId(master.getId()).build();

    private GameCharacter storedNpc(CharacterVisibility visibility) {
        GameCharacter npc = GameCharacter.builder().owner(master).adventure(mastered).name("Innkeeper")
                .npc(true).visibility(visibility).build();
        when(spi.findById(npc.getId())).thenReturn(Optional.of(npc));
        return npc;
    }

    @Test
    void the_master_reads_a_private_npc() {
        GameCharacter npc = storedNpc(CharacterVisibility.PRIVATE);

        Assertions.assertEquals(Optional.of(npc), service.findById(master.getId(), npc.getId()));
    }

    @Test
    void a_player_never_reads_a_private_npc_by_id_or_by_picture_and_learns_nothing_of_it() {
        GameCharacter npc = storedNpc(CharacterVisibility.PRIVATE);

        Assertions.assertEquals(Optional.empty(), service.findById(other.getId(), npc.getId()));
        Assertions.assertThrows(GameCharacterNotFoundException.class, () -> service.findPicture(other.getId(), npc.getId()));
        verify(adventureAccessVerifierApi, never()).verifyParticipant(any(), any());
    }

    @Test
    void a_participant_reads_a_public_npc() {
        GameCharacter npc = storedNpc(CharacterVisibility.PUBLIC);
        when(adventureAccessVerifierApi.verifyParticipant(other.getId(), mastered.getId())).thenReturn(mastered);

        Assertions.assertEquals(Optional.of(npc), service.findById(other.getId(), npc.getId()));
    }

    @Test
    void the_private_npcs_are_listed_for_the_current_master_only() {
        when(adventureAccessVerifierApi.verifyParticipant(master.getId(), mastered.getId())).thenReturn(mastered);
        when(adventureAccessVerifierApi.verifyParticipant(other.getId(), mastered.getId())).thenReturn(mastered);
        GameCharacter npc = GameCharacter.builder().owner(master).adventure(mastered).name("Innkeeper")
                .npc(true).visibility(CharacterVisibility.PRIVATE).build();
        when(spi.findVisibleByAdventureId(mastered.getId(), true)).thenReturn(java.util.List.of(npc));
        when(spi.findVisibleByAdventureId(mastered.getId(), false)).thenReturn(java.util.List.of());

        Assertions.assertEquals(java.util.List.of(npc), service.findByAdventureId(master.getId(), mastered.getId()));
        Assertions.assertEquals(java.util.List.of(), service.findByAdventureId(other.getId(), mastered.getId()));
    }

    @Test
    void a_vacant_seat_gives_nobody_the_private_npcs() {
        Adventure vacant = mastered.withMasterVacantSince(java.time.Instant.now());
        GameCharacter npc = GameCharacter.builder().owner(master).adventure(vacant).name("Innkeeper")
                .npc(true).visibility(CharacterVisibility.PRIVATE).build();
        when(spi.findById(npc.getId())).thenReturn(Optional.of(npc));

        Assertions.assertEquals(Optional.empty(), service.findById(master.getId(), npc.getId()));
    }
}
