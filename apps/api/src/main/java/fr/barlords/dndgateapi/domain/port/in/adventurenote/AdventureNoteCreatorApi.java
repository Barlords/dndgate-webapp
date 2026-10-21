package fr.barlords.dndgateapi.domain.port.in.adventurenote;

import fr.barlords.dndgateapi.domain.functional.model.adventure.note.AdventureNote;

import java.util.UUID;

public interface AdventureNoteCreatorApi {

    AdventureNote create(UUID accountId, UUID adventureId, AdventureNote adventureNote);

}
