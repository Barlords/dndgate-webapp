package fr.barlords.dndgateapi.domain.port.in.adventurenote;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.domain.functional.model.adventure.note.AdventureNote;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdventureNoteFinderApi {

    /**
     * Participants only. A private note is only readable by the adventure owner.
     */
    Optional<AdventureNote> findById(UUID accountId, UUID id);

    /**
     * Participants only. Private notes are only returned to the adventure owner.
     */
    Page<AdventureNote> findByAdventureId(UUID accountId, UUID adventureId, PageRequest request);

}
