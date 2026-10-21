package fr.barlords.dndgateapi.domain.port.in.adventure.master;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.IssuedMasterInvitation;

import java.util.UUID;

public interface MasterInvitationApi {

    /** Game master only. Replaces the invitation that was pending, if any. */
    IssuedMasterInvitation invite(UUID accountId, UUID adventureId);

    /** Game master only. */
    void revoke(UUID accountId, UUID adventureId);

    /** Any account holding the code. The game master becomes a player if there is a seat left. */
    Adventure accept(UUID accountId, String code);
}
