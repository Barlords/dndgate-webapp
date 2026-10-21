package fr.barlords.dndgateapi.domain.port.in.adventure.master;

import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaimDetails;

import java.util.Optional;
import java.util.UUID;

public interface MasterClaimApi {

    /** A player asks for the role. Only possible when the game master left or deleted the account. */
    MasterClaimDetails claim(UUID accountId, UUID adventureId);

    /** The other players only. One refusal ends the vote; when everybody approved, the role changes hands. */
    MasterClaimDetails vote(UUID accountId, UUID adventureId, boolean approves);

    /** The candidate gives up. */
    void cancel(UUID accountId, UUID adventureId);

    /** Participants only. */
    Optional<MasterClaimDetails> findOpen(UUID accountId, UUID adventureId);
}
