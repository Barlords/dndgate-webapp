package fr.barlords.dndgateapi.domain.port.in.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;

import java.io.InputStream;
import java.util.UUID;

public interface GameCharacterCreatorApi {
    GameCharacter create(UUID accountId, UUID adventureId, GameCharacter gameCharacter, InputStream pictureInputStream);
}
