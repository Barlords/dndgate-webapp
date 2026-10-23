package fr.barlords.dndgateapi.domain.functional.service.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureAccessDeniedException;
import fr.barlords.dndgateapi.domain.functional.exception.gamecharacter.GameCharacterOwnerException;
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
}
