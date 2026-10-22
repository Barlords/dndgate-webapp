package fr.barlords.dndgateapi.domain.functional.service.gamecharacter;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.service.DeletionConfirmation;
import fr.barlords.dndgateapi.domain.port.in.gamecharacter.GameCharacterDeleterApi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class GameCharacterDeleterService implements GameCharacterDeleterApi {

    private final GameCharacterPersistenceSpi spi;

    private final GameCharacterOwnerVerifierService gameCharacterOwnerVerifierService;

    private final MinioSpi minioSpi;

    private final AuditSpi audit;

    @Override
    public void deleteById(UUID accountId, UUID id, String confirmation) {
        GameCharacter gameCharacter = gameCharacterOwnerVerifierService.verify(accountId, id);

        DeletionConfirmation.requireName(gameCharacter.getName(), confirmation);

        spi.deleteById(id);
        audit.deletion("character", accountId, id);

        // After the row: a storage failure must neither block the deletion nor leave a character without picture
        if (gameCharacter.getPicturePath() != null) {
            minioSpi.deleteAfterCommit(gameCharacter.getPicturePath());
        }
    }
}
