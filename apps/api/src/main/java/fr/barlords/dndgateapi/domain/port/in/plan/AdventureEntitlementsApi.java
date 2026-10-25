package fr.barlords.dndgateapi.domain.port.in.plan;

import fr.barlords.dndgateapi.domain.functional.model.plan.AdventureEntitlements;

import java.util.UUID;

public interface AdventureEntitlementsApi {

    /**
     * The features that work for the account in the adventure and the usage of the adventure limits of the game master's plan.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureAccessDeniedException if the account is not a participant
     */
    AdventureEntitlements of(UUID accountId, UUID adventureId);
}
