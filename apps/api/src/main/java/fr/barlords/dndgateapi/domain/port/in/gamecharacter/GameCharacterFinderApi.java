package fr.barlords.dndgateapi.domain.port.in.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.model.GameCharacterPicture;

import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameCharacterFinderApi {

    /**
     * Readable by the character owner and by the participants of its adventure.
     */
    Optional<GameCharacter> findById(UUID accountId, UUID id);

    /**
     * A caller can only list its own characters.
     */
    List<GameCharacter> findByOwnerId(UUID accountId, UUID ownerId);

    /**
     * Participants only.
     */
    List<GameCharacter> findByAdventureId(UUID accountId, UUID adventureId);

    List<GameCharacter> findMyCharacter(UUID accountId);

    /**
     * Same rule as {@link #findById}: the owner and the participants of the adventure. The pictures are not public:
     * this is the only way to read one.
     */
    GameCharacterPicture findPicture(UUID accountId, UUID id);

}
