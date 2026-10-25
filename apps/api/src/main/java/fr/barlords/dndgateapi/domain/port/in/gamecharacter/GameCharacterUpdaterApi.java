package fr.barlords.dndgateapi.domain.port.in.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.model.CharacterVisibility;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;

import java.util.UUID;

public interface GameCharacterUpdaterApi {

    GameCharacter update(UUID accountId, GameCharacter gameCharacter);

    /** Makes a non-player character private or public; only its current game master may. */
    GameCharacter setVisibility(UUID accountId, UUID characterId, CharacterVisibility visibility);

}
