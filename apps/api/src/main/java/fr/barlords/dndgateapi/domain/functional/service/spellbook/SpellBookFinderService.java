package fr.barlords.dndgateapi.domain.functional.service.spellbook;

import fr.barlords.dndgateapi.domain.functional.exception.spellbook.SpellBookOwnerException;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.port.in.spellbook.SpellBookFinderApi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class SpellBookFinderService implements SpellBookFinderApi {

    private final SpellBookPersistenceSpi spi;

    @Override
    public Optional<SpellBook> findById(UUID accountId, UUID id) {
        return spi.findById(id).map(spellBook -> {
            if (!accountId.equals(spellBook.getOwner().getId())) {
                throw new SpellBookOwnerException();
            }
            return spellBook;
        });
    }

    @Override
    public List<SpellBook> findByOwnerId(UUID accountId, UUID ownerId) {
        if (!accountId.equals(ownerId)) {
            throw new SpellBookOwnerException();
        }

        return spi.findByOwnerId(ownerId);
    }

    @Override
    public List<SpellBook> findMyBook(UUID accountId) {
        return spi.findByOwnerId(accountId);
    }

}
