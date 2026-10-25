package fr.barlords.dndgateapi.domain.functional.service.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.exception.gamecharacter.GameCharacterNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.gamecharacter.GameCharacterOwnerException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class GameCharacterOwnerVerifierService {

    private final GameCharacterPersistenceSpi spi;

    public GameCharacter verify(UUID accountId, UUID gameCharacterId) {
        GameCharacter gameCharacter = spi.findById(gameCharacterId)
                .orElseThrow(GameCharacterNotFoundException::new);

        if (gameCharacter.isNpc()) {
            // Managed by the current game master, whoever created it; a private one is not even known to the others
            if (gameCharacter.isManagedBy(accountId)) {
                return gameCharacter;
            }
            throw gameCharacter.isPrivateNpc() ? new GameCharacterNotFoundException() : new GameCharacterOwnerException();
        }

        if (!accountId.equals(gameCharacter.getOwner().getId())) {
            throw new GameCharacterOwnerException();
        }

        return gameCharacter;
    }

}
