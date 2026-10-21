package fr.barlords.dndgateapi.domain.functional.service.adventurenote;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.exception.QuantityLimitExceededException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.AdventureNote;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.port.in.adventurenote.AdventureNoteCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.AdventureNotePersistenceSpi;
import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AdventureNoteCreatorService implements AdventureNoteCreatorApi {

    private final AdventureNotePersistenceSpi spi;

    private final AdventureOwnerVerifierService adventureOwnerVerifierService;

    private final QuantityLimits limits;

    @Override
    public AdventureNote create(UUID accountId, UUID adventureId, AdventureNote adventureNote) {
        Adventure adventure = adventureOwnerVerifierService.verify(accountId, adventureId);

        if (spi.countByAdventureId(adventureId) >= limits.adventureNotesPerAdventure()) {
            throw QuantityLimitExceededException.adventureNotes(limits.adventureNotesPerAdventure());
        }

        return spi.save(adventureNote.withAdventure(adventure))
                .getOrElseThrow(DataNotSaveException::new);
    }
}
