package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.AccountChanges;
import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;
import fr.barlords.dndgateapi.domain.port.in.account.AccountUpdaterApi;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The domain knows nothing about transactions: this decorator makes a change of the account one unit. A new
 * password is never kept while the sessions opened with the old one survive. A failed password check is still
 * counted: the failures are written in their own transaction.
 */
@RequiredArgsConstructor
public class TransactionalAccountUpdater implements AccountUpdaterApi {

    private final AccountUpdaterApi delegate;

    @Override
    @Transactional
    public Account update(UUID accountId, String currentPassword, AccountChanges changes, ClientAddress client) {
        return delegate.update(accountId, currentPassword, changes, client);
    }

}
