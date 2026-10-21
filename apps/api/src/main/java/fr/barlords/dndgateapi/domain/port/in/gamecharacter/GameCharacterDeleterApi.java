package fr.barlords.dndgateapi.domain.port.in.gamecharacter;

import java.util.UUID;

public interface GameCharacterDeleterApi {

    /** Only the owner of the character can delete it, and only by retyping its name. */
    void deleteById(UUID accountId, UUID gameCharacterId, String confirmation);

}
