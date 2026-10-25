package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameCharacterPersistenceSpi extends PersistenceSpi<GameCharacter, UUID> {

    /** The characters a player owns (the non-player characters of a game master are not among them). */
    List<GameCharacter> findByOwnerId(UUID ownerId);

    /** Every character of the adventure, private non-player characters included. */
    List<GameCharacter> findByAdventureId(UUID adventureId);

    /** The characters of the adventure a reader may know of: the private non-player ones only for the current game master. */
    List<GameCharacter> findVisibleByAdventureId(UUID adventureId, boolean withPrivateNpcs);

    long countNpcsByAdventureId(UUID adventureId);

    long countByOwnerIdAndAdventureId(UUID ownerId, UUID adventureId);

    /** The character the owner plays in the adventure, if they chose one. */
    Optional<GameCharacter> findActive(UUID ownerId, UUID adventureId);

    long countByOwnerIdWithoutAdventure(UUID ownerId);

    /** Takes the characters of the players back to the reserve of their owners (no adventure, not active); non-player characters stay. */
    void moveToReserveByAdventureId(UUID adventureId);
}
