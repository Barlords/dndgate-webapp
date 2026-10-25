package fr.barlords.dndgateapi.domain.port.in.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;

import java.util.UUID;

public interface GameCharacterMoverApi {

    /**
     * Takes the character out of its adventure: it joins the reserve of its owner and stops being the active one.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.plan.PlanLimitReachedException if the reserve is full
     */
    GameCharacter moveToReserve(UUID accountId, UUID characterId);

    /**
     * Moves a character of the reserve into an adventure the caller plays in; it becomes the active one when the
     * caller has no other character there.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.plan.PlanLimitReachedException if the plan of the master does not allow another one
     */
    GameCharacter moveToAdventure(UUID accountId, UUID characterId, UUID adventureId);

    /** Makes the character the one the caller plays in the adventure; the previous active one is deactivated. */
    GameCharacter activate(UUID accountId, UUID adventureId, UUID characterId);

}
