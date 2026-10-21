package fr.barlords.dndgateapi.domain.port.in.adventure.master;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;

import java.util.UUID;

public interface MasterStepDownApi {

    /**
     * The game master gives up the role: nobody is master until someone is invited or the players vote.
     * Hard to undo, so the name of the adventure has to be retyped.
     */
    Adventure stepDown(UUID accountId, UUID adventureId, String confirmation);
}
