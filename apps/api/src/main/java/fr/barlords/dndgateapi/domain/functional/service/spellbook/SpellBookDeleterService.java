package fr.barlords.dndgateapi.domain.functional.service.spellbook;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.functional.service.DeletionConfirmation;
import fr.barlords.dndgateapi.domain.port.in.spellbook.SpellBookDeleterApi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class SpellBookDeleterService implements SpellBookDeleterApi {

    private final SpellBookPersistenceSpi spi;

    private final SpellBookOwnerVerifierService spellBookOwnerVerifierService;

    private final AuditSpi audit;

    @Override
    public void deleteById(UUID accountId, UUID id, String confirmation) {
        SpellBook spellBook = spellBookOwnerVerifierService.verify(accountId, id);

        DeletionConfirmation.requireName(spellBook.getName(), confirmation);

        spi.deleteById(id);
        audit.deletion("spellbook", accountId, id);
    }
}
