package fr.barlords.dndgateapi.domain.functional.service.adventurenote;

import java.util.Objects;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.note.AdventureNoteNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.AdventureNote;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.port.in.adventurenote.AdventureNoteUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.AdventureNotePersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AdventureNoteUpdaterService implements AdventureNoteUpdaterApi {

    private final AdventureNotePersistenceSpi spi;

    private final AdventureOwnerVerifierService adventureOwnerVerifierService;

    @Override
    public AdventureNote update(UUID accountId, UUID adventureId, AdventureNote adventureNote) {
        AdventureNote previousAdventureNote = spi.findById(adventureNote.getId())
                .orElseThrow(AdventureNoteNotFoundException::new);

        // The right to change a note comes from the adventure the note belongs to, not from the one named in the request
        adventureOwnerVerifierService.verify(accountId, previousAdventureNote.getAdventure().getId());

        AdventureNote patchedAdventureNote = previousAdventureNote
                .withDate(Objects.requireNonNullElse(adventureNote.getDate(), previousAdventureNote.getDate()))
                .withContent(Objects.requireNonNullElse(adventureNote.getContent(), previousAdventureNote.getContent()))
                .withNoteVisibility(Objects.requireNonNullElse(adventureNote.getNoteVisibility(), previousAdventureNote.getNoteVisibility()));

        return spi.save(patchedAdventureNote)
                .getOrElseThrow(DataNotSaveException::new);
    }
}
