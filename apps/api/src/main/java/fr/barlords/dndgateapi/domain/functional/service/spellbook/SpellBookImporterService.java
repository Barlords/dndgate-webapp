package fr.barlords.dndgateapi.domain.functional.service.spellbook;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.spellbook.SpellBookImporterApi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class SpellBookImporterService implements SpellBookImporterApi {

    private final SpellBookPersistenceSpi spi;

    private final CurrentAccountService currentAccountService;

    @Override
    public SpellBook importSpellBook(UUID accountId, SpellBook spellBook) {
        Account account = currentAccountService.getAccountById(accountId);

        return spi.save(spellBook.withOwner(account))
                .getOrElseThrow(DataNotSaveException::new);
    }
}
