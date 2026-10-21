package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;

import java.util.List;
import java.util.UUID;

public interface GameCharacterPersistenceSpi extends PersistenceSpi<GameCharacter, UUID> {

    List<GameCharacter> findByOwnerId(UUID ownerId);

    List<GameCharacter> findByAdventureId(UUID adventureId);

}
