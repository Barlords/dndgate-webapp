package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;
import fr.barlords.dndgateapi.domain.functional.service.account.AccountAnonymizerService;
import fr.barlords.dndgateapi.domain.port.in.account.AccountAnonymizerApi;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * The domain knows nothing about transactions: this decorator makes the whole anonymization one unit,
 * either everything is done or nothing is.
 * The password is checked first, outside of the transaction, for the same reason as in
 * {@link TransactionalAccountUpdater}: a check inside would hold two pooled connections at once.
 */
public class TransactionalAccountAnonymizer implements AccountAnonymizerApi {

    private final AccountAnonymizerService delegate;

    private final TransactionTemplate transaction;

    public TransactionalAccountAnonymizer(AccountAnonymizerService delegate, PlatformTransactionManager transactionManager) {
        this.delegate = delegate;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void anonymize(UUID accountId, String password, ClientAddress client) {
        delegate.verify(accountId, password, client).ifPresent(account -> {
            // the payment provider is called outside the transaction, before anything is written
            delegate.cancelSubscription(account);
            transaction.executeWithoutResult(status -> delegate.apply(account));
        });
    }

}
