package fr.barlords.dndgateapi.domain.port.in.adventure;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;

import java.util.UUID;

public interface AdventureAccessVerifierApi {

    /**
     * Checks that the account is the owner or a player of the adventure.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotFoundException if the adventure does not exist
     * @throws fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureAccessDeniedException if the account is not a participant
     */
    Adventure verifyParticipant(UUID accountId, UUID adventureId);

}
