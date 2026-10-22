package fr.barlords.dndgateapi.domain.functional.service.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameCharacterDeleterServiceTest {

    @Mock
    private GameCharacterPersistenceSpi spi;

    @Mock
    private GameCharacterOwnerVerifierService ownerVerifier;

    @Mock
    private MinioSpi minioSpi;

    @Mock
    private AuditSpi audit;

    private final UUID accountId = UUID.randomUUID();

    private GameCharacterDeleterService service() {
        return new GameCharacterDeleterService(spi, ownerVerifier, minioSpi, audit);
    }

    private GameCharacter character(String picturePath) {
        GameCharacter character = GameCharacter.builder().name("Gandalf").picturePath(picturePath).build();
        when(ownerVerifier.verify(accountId, character.getId())).thenReturn(character);
        return character;
    }

    @Test
    void the_row_is_deleted_first_and_the_picture_removal_is_deferred_to_the_commit() throws Exception {
        GameCharacter character = character("account/1/g.png");

        service().deleteById(accountId, character.getId(), "Gandalf");

        InOrder order = inOrder(spi, audit, minioSpi);
        order.verify(spi).deleteById(character.getId());
        order.verify(audit).deletion("character", accountId, character.getId());
        order.verify(minioSpi).deleteAfterCommit("account/1/g.png");
        verify(minioSpi, never()).deleteFile(any());
    }

    @Test
    void a_character_without_picture_removes_nothing() {
        GameCharacter character = character(null);

        service().deleteById(accountId, character.getId(), "Gandalf");

        verify(spi).deleteById(character.getId());
        verify(minioSpi, never()).deleteAfterCommit(any());
    }

    @Test
    void the_picture_is_kept_when_the_row_cannot_be_deleted() {
        GameCharacter character = character("account/1/g.png");
        doThrow(new IllegalStateException("database refused")).when(spi).deleteById(character.getId());

        Assertions.assertThrows(IllegalStateException.class, () -> service().deleteById(accountId, character.getId(), "Gandalf"));

        verify(minioSpi, never()).deleteAfterCommit(any());
    }
}
