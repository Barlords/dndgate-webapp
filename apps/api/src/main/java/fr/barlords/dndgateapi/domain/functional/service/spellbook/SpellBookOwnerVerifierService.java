package fr.barlords.dndgateapi.domain.functional.service.spellbook;

import fr.barlords.dndgateapi.domain.functional.exception.spellbook.SpellBookNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.spellbook.SpellBookOwnerException;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class SpellBookOwnerVerifierService {

    private final SpellBookPersistenceSpi spi;

    public SpellBook verify(UUID accountId, UUID spellBookId) {
        SpellBook spellBook = spi.findById(spellBookId)
                .orElseThrow(SpellBookNotFoundException::new);

        if (!accountId.equals(spellBook.getOwner().getId())) {
            throw new SpellBookOwnerException();
        }

        return spellBook;
    }

}
