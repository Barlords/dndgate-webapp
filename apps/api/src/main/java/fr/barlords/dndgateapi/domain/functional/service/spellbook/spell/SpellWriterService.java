package fr.barlords.dndgateapi.domain.functional.service.spellbook.spell;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.exception.QuantityLimitExceededException;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.functional.service.spellbook.SpellBookOwnerVerifierService;
import fr.barlords.dndgateapi.domain.port.in.spellbook.spell.SpellWriterApi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class SpellWriterService implements SpellWriterApi {

    private final SpellBookPersistenceSpi spi;

    private final SpellBookOwnerVerifierService spellBookOwnerVerifierService;

    private final QuantityLimits limits;

    @Override
    public SpellBook addSpell(UUID accountId, UUID spellBookId, String spellIndex) {
        SpellBook spellBook = spellBookOwnerVerifierService.verify(accountId, spellBookId);

        Set<String> updatedSpellList = new HashSet<>(spellBook.getSpellList());
        if (!updatedSpellList.contains(spellIndex) && updatedSpellList.size() >= limits.spellsPerSpellBook()) {
            throw QuantityLimitExceededException.spells(limits.spellsPerSpellBook());
        }
        updatedSpellList.add(spellIndex);

        return spi.save(spellBook.withSpellList(updatedSpellList))
                .getOrElseThrow(DataNotSaveException::new);
    }
}
