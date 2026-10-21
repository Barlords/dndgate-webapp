package fr.barlords.dndgateapi.domain.port.in.adventure;

import java.util.UUID;

public interface AdventureDeleterApi {

    /**
     * Deletes the adventure with everything that belongs to it: characters, notes, messages, planning.
     * Only its owner can, once it is not in progress, and only by retyping its name.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.DeletionNotConfirmedException when the name does not match
     * @throws fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureInProgressException when it is running
     */
    void deleteById(UUID accountId, UUID id, String confirmation);

}
