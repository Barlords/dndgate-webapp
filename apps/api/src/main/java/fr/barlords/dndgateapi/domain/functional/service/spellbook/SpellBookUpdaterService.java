package fr.barlords.dndgateapi.domain.functional.service.spellbook;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.port.in.spellbook.SpellBookUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class SpellBookUpdaterService implements SpellBookUpdaterApi {

    private final SpellBookPersistenceSpi spi;

    private final SpellBookOwnerVerifierService spellBookOwnerVerifierService;

    @Override
    public SpellBook update(UUID accountId, SpellBook spellBook) {
        SpellBook previousSpellBook = spellBookOwnerVerifierService.verify(accountId, spellBook.getId());

        SpellBook patchedSpellBook = previousSpellBook
                .withName(spellBook.getName())
                .withDescription(spellBook.getDescription());

        return spi.save(patchedSpellBook)
                .getOrElseThrow(DataNotSaveException::new);
    }
}
