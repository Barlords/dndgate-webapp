package fr.barlords.dndgateapi.server.audit;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs an action once the current transaction is committed. If the transaction is rolled back, the action never runs.
 * Outside a transaction there is nothing to wait for: the action runs at once.
 */
public final class AfterCommit implements TransactionSynchronization {

    private final Runnable action;

    private AfterCommit(Runnable action) {
        this.action = action;
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new AfterCommit(action));
        } else {
            action.run();
        }
    }

    @Override
    public void afterCommit() {
        action.run();
    }

}
