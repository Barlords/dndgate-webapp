package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameCharacterPersistenceSpi extends PersistenceSpi<GameCharacter, UUID> {

    List<GameCharacter> findByOwnerId(UUID ownerId);

    List<GameCharacter> findByAdventureId(UUID adventureId);

    long countByOwnerIdAndAdventureId(UUID ownerId, UUID adventureId);

    /** The character the owner plays in the adventure, if they chose one. */
    Optional<GameCharacter> findActive(UUID ownerId, UUID adventureId);

    long countByOwnerIdWithoutAdventure(UUID ownerId);
}
