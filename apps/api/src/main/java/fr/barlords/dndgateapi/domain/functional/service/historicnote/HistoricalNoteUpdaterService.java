package fr.barlords.dndgateapi.domain.functional.service.historicnote;

import java.util.Objects;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.note.AdventureNoteNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.HistoricalNote;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.port.in.historicalnote.HistoricalNoteUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.HistoricalNotePersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class HistoricalNoteUpdaterService implements HistoricalNoteUpdaterApi {

    private final HistoricalNotePersistenceSpi spi;

    private final AdventureOwnerVerifierService adventureOwnerVerifierService;

    @Override
    public HistoricalNote update(UUID accountId, HistoricalNote historicalNote) {
        HistoricalNote previousHistoricalNote = spi.findById(historicalNote.getId())
                .orElseThrow(AdventureNoteNotFoundException::new);

        // The right to change a note comes from the adventure the note belongs to, not from the one named in the request
        adventureOwnerVerifierService.verify(accountId, previousHistoricalNote.getAdventure().getId());

        HistoricalNote patchedHistoricalNote = previousHistoricalNote
                .withDate(Objects.requireNonNullElse(historicalNote.getDate(), previousHistoricalNote.getDate()))
                .withContent(Objects.requireNonNullElse(historicalNote.getContent(), previousHistoricalNote.getContent()));

        return spi.save(patchedHistoricalNote);
    }
}
