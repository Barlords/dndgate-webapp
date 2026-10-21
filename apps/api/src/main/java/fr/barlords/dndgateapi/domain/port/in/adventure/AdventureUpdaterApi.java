package fr.barlords.dndgateapi.domain.port.in.adventure;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;

import java.util.UUID;

public interface AdventureUpdaterApi {

    Adventure update(UUID accountId, Adventure adventure);
    Adventure updateVisibility(UUID accountId, UUID id, AdventureVisibility visibility);
    Adventure updateStatus(UUID accountId, UUID id, AdventureStatus status);
    Adventure joinAdventure(UUID adventureId, UUID accountId);
    Adventure joinAdventureByCode(UUID accountId, String code);
    void sendInvitationCode(UUID accountId, UUID adventureId, String targetPseudo);
    Adventure quitAdventure(UUID adventureId, UUID accountId);

}
