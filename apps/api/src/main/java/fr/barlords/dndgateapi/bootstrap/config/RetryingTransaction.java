package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureConcurrentModificationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Runs an operation in a transaction and, if someone changed the same data at the same moment, runs it again
 * (from a fresh read, in a new transaction). The attempts are outside of the transaction on purpose: a transaction
 * that met a conflict cannot be reused.
 */
@Component
public class RetryingTransaction {

    private static final int MAX_ATTEMPTS = 10;

    private final TransactionTemplate template;

    public RetryingTransaction(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    public <T> T execute(Supplier<T> operation) {
        for (int remaining = MAX_ATTEMPTS - 1; ; remaining--) {
            try {
                return template.execute(status -> operation.get());
            } catch (AdventureConcurrentModificationException e) {
                if (remaining == 0) {
                    throw e;
                }
            }
        }
    }

    public void run(Runnable operation) {
        execute(() -> {
            operation.run();
            return null;
        });
    }
}
