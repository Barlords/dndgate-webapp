package fr.barlords.dndgateapi.domain.functional.service.adventurenote;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureOwnerException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.AdventureNote;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.NoteVisibility;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.adventurenote.AdventureNoteFinderApi;
import fr.barlords.dndgateapi.domain.port.out.AdventureNotePersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AdventureNoteFinderService implements AdventureNoteFinderApi {

    private final AdventureNotePersistenceSpi spi;

    private final AdventureAccessVerifierApi adventureAccessVerifierApi;

    @Override
    public Optional<AdventureNote> findById(UUID accountId, UUID id) {
        return spi.findById(id).map(note -> {
            Adventure adventure = adventureAccessVerifierApi.verifyParticipant(accountId, note.getAdventure().getId());

            if (isPrivate(note) && !adventure.isMaster(accountId)) {
                throw new AdventureOwnerException();
            }

            return note;
        });
    }

    @Override
    public Page<AdventureNote> findByAdventureId(UUID accountId, UUID adventureId, PageRequest request) {
        Adventure adventure = adventureAccessVerifierApi.verifyParticipant(accountId, adventureId);

        return spi.findByAdventureId(adventureId, adventure.isMaster(accountId), request);
    }

    private boolean isPrivate(AdventureNote note) {
        return NoteVisibility.PRIVATE.equals(note.getNoteVisibility());
    }

}
