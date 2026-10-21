package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.model.planning.Availability;
import fr.barlords.dndgateapi.domain.port.in.planning.AvailabilityUpdaterApi;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

/**
 * Voting is "find the vote of this account, then create or update it". When two first votes of the same account
 * arrive together, the unique index rejects the second one at commit: the operation is replayed in a new
 * transaction, where it now finds the vote and updates it.
 */
@RequiredArgsConstructor
public class TransactionalAvailabilityVote implements AvailabilityUpdaterApi {

    private final AvailabilityUpdaterApi delegate;

    private final RetryingTransaction transaction;

    @Override
    public Availability createOrUpdate(UUID accountId, Availability availability) {
        return transaction.execute(() -> delegate.createOrUpdate(accountId, availability), DataIntegrityViolationException.class);
    }
}
