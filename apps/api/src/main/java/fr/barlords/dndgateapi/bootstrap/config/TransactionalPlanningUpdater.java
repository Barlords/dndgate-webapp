package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.model.Upserted;
import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;
import fr.barlords.dndgateapi.domain.port.in.planning.PlanningUpdaterApi;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

/**
 * Sending the planning of an adventure is "find it, then create or update it", in one transaction. When two first
 * plannings of the same adventure arrive together, the unique index refuses the second one: the operation is
 * replayed, where it now finds the planning and updates it. A planning changed by someone else between the read
 * and the write is not replayed: the caller gets a 409 and sends it again knowingly.
 */
@RequiredArgsConstructor
public class TransactionalPlanningUpdater implements PlanningUpdaterApi {

    private final PlanningUpdaterApi delegate;

    private final RetryingTransaction transaction;

    @Override
    public Upserted<Planning> createOrUpdate(UUID accountId, Planning planning) {
        return transaction.execute(() -> delegate.createOrUpdate(accountId, planning), DataIntegrityViolationException.class);
    }
}
