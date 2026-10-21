package fr.barlords.dndgateapi.domain.functional.service;

import fr.barlords.dndgateapi.domain.functional.exception.account.TokenNotValidException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Resolves the authenticated account from its identifier.
 * Authentication itself (token verification) is done by the inbound adapter.
 */
@RequiredArgsConstructor
public class CurrentAccountService {

    private final AccountPersistenceSpi spi;

    public Account getAccountById(UUID accountId) {
        return spi.findById(accountId)
                .orElseThrow(TokenNotValidException::new);
    }

}
