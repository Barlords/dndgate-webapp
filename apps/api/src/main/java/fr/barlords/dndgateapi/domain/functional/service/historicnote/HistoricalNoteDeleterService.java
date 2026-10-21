package fr.barlords.dndgateapi.domain.functional.service.historicnote;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureOwnerException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.note.HistoricalNoteNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.HistoricalNote;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.DeletionConfirmation;
import fr.barlords.dndgateapi.domain.port.in.historicalnote.HistoricalNoteDeleterApi;
import fr.barlords.dndgateapi.domain.port.out.HistoricalNotePersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class HistoricalNoteDeleterService implements HistoricalNoteDeleterApi {

    private final HistoricalNotePersistenceSpi spi;

    private final CurrentAccountService currentAccountService;

    private final AuditSpi audit;

    @Override
    public void deleteById(UUID accountId, UUID id, boolean confirmed) {
        Account account = currentAccountService.getAccountById(accountId);

        HistoricalNote historicalNote = spi.findById(id)
                .orElseThrow(HistoricalNoteNotFoundException::new);

        if (historicalNote.getAdventure().isMyOwner(account)) {
            DeletionConfirmation.requireConfirmed(confirmed);
            spi.deleteById(id);
            audit.deletion("historical-note", accountId, id);
        } else {
            throw new AdventureOwnerException();
        }
    }
}
