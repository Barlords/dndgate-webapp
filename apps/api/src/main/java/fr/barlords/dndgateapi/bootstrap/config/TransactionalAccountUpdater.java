package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.AccountChanges;
import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;
import fr.barlords.dndgateapi.domain.functional.service.account.AccountUpdaterService;
import fr.barlords.dndgateapi.domain.port.in.account.AccountUpdaterApi;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * The domain knows nothing about transactions: this decorator makes the writes of a change of the account one unit,
 * so that a new password is never kept while the sessions opened with the old one survive.
 * The current password is checked first, outside of the transaction: the check hashes a password and records a
 * failure in a transaction of its own, which inside would hold two pooled connections at once (a few simultaneous
 * wrong passwords would empty the pool, and no failure would be recorded). The changes are applied to the account
 * read by the check: if it changed in between, its version refuses the save.
 */
public class TransactionalAccountUpdater implements AccountUpdaterApi {

    private final AccountUpdaterService delegate;

    private final TransactionTemplate transaction;

    public TransactionalAccountUpdater(AccountUpdaterService delegate, PlatformTransactionManager transactionManager) {
        this.delegate = delegate;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public Account update(UUID accountId, String currentPassword, AccountChanges changes, ClientAddress client) {
        Account verified = delegate.verify(accountId, currentPassword, client);
        return transaction.execute(status -> delegate.apply(verified, currentPassword, changes));
    }

}
