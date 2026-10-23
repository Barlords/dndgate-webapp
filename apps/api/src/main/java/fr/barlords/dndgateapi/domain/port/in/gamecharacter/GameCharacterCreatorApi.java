package fr.barlords.dndgateapi.domain.port.in.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;

import java.io.InputStream;
import java.util.UUID;

public interface GameCharacterCreatorApi {

    /**
     * With an adventure, the caller must take part in it. Without one ({@code adventureId} null), the character is
     * kept outside any adventure of the application: only its owner reads it.
     */
    GameCharacter create(UUID accountId, UUID adventureId, GameCharacter gameCharacter, InputStream pictureInputStream);
}
