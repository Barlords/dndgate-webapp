package fr.barlords.dndgateapi.domain.port.in.adventurenote;

import java.util.UUID;

public interface AdventureNoteDeleterApi {

    /** A note has no name to retype: the caller has to say explicitly that it must be deleted. */
    void deleteById(UUID accountId, UUID id, boolean confirmed);

}
