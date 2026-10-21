package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;
import fr.barlords.dndgateapi.domain.port.in.account.AccountAnonymizerApi;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The domain knows nothing about transactions: this decorator makes the whole anonymization one unit,
 * either everything is done or nothing is.
 */
@RequiredArgsConstructor
public class TransactionalAccountAnonymizer implements AccountAnonymizerApi {

    private final AccountAnonymizerApi delegate;

    @Override
    @Transactional
    public void anonymize(UUID accountId, String password, ClientAddress client) {
        delegate.anonymize(accountId, password, client);
    }

}
