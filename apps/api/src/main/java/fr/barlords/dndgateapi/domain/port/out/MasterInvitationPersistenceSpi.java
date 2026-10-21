package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterInvitation;

import java.util.Optional;
import java.util.UUID;

public interface MasterInvitationPersistenceSpi {

    /** One invitation at a time per adventure: the previous one, if any, stops working. */
    MasterInvitation replace(MasterInvitation invitation);

    Optional<MasterInvitation> findByCodeHash(String codeHash);

    /** Atomic: of two people using the same code at the same moment, only one gets true. */
    boolean consume(UUID invitationId);

    void deleteByAdventureId(UUID adventureId);
}
