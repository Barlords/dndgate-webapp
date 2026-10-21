package fr.barlords.dndgateapi.domain.port.in.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;

import java.util.UUID;

public interface GameCharacterUpdaterApi {

    GameCharacter update(UUID accountId, GameCharacter gameCharacter);

}
