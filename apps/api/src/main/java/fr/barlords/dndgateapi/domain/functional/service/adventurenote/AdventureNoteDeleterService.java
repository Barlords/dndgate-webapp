package fr.barlords.dndgateapi.domain.functional.service.adventurenote;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureOwnerException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.note.AdventureNoteNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.AdventureNote;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.DeletionConfirmation;
import fr.barlords.dndgateapi.domain.port.in.adventurenote.AdventureNoteDeleterApi;
import fr.barlords.dndgateapi.domain.port.out.AdventureNotePersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AdventureNoteDeleterService implements AdventureNoteDeleterApi {

    private final AdventureNotePersistenceSpi spi;

    private final CurrentAccountService currentAccountService;

    private final AuditSpi audit;

    @Override
    public void deleteById(UUID accountId, UUID id, boolean confirmed) {
        Account account = currentAccountService.getAccountById(accountId);

        AdventureNote adventureNote = spi.findById(id)
                .orElseThrow(AdventureNoteNotFoundException::new);

        if (adventureNote.getAdventure().isMyOwner(account)) {
            DeletionConfirmation.requireConfirmed(confirmed);
            spi.deleteById(id);
            audit.deletion("adventure-note", accountId, id);
        } else {
            throw new AdventureOwnerException();
        }
    }
}
