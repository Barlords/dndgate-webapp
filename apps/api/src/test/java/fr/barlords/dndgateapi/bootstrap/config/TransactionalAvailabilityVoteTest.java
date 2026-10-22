package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.model.Upserted;
import fr.barlords.dndgateapi.domain.functional.model.planning.Availability;
import fr.barlords.dndgateapi.domain.port.in.planning.AvailabilityUpdaterApi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionalAvailabilityVoteTest {

    private final UUID accountId = UUID.randomUUID();

    private final Availability vote = Availability.builder().planningId(UUID.randomUUID()).build();

    private final PlanningTransactions transactions = new PlanningTransactions();

    private static class PlanningTransactions {
        final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);

        PlanningTransactions() {
            when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        }
    }

    private TransactionalAvailabilityVote decorate(AvailabilityUpdaterApi delegate) {
        return new TransactionalAvailabilityVote(delegate, new RetryingTransaction(transactions.manager));
    }

    @Test
    void a_concurrent_first_vote_is_replayed_and_then_succeeds() {
        AtomicInteger calls = new AtomicInteger();
        TransactionalAvailabilityVote service = decorate((account, availability) -> {
            if (calls.incrementAndGet() == 1) {
                throw new DataIntegrityViolationException("duplicate key");
            }
            return Upserted.created(availability);
        });

        Assertions.assertSame(vote, service.createOrUpdate(accountId, vote).value());
        Assertions.assertEquals(2, calls.get());
    }

    @Test
    void a_violation_that_never_goes_away_is_finally_reported() {
        AtomicInteger calls = new AtomicInteger();
        TransactionalAvailabilityVote service = decorate((account, availability) -> {
            calls.incrementAndGet();
            throw new DataIntegrityViolationException("duplicate key");
        });

        Assertions.assertThrows(DataIntegrityViolationException.class, () -> service.createOrUpdate(accountId, vote));
        Assertions.assertEquals(10, calls.get());
    }

    @Test
    void any_other_error_is_not_replayed() {
        AtomicInteger calls = new AtomicInteger();
        TransactionalAvailabilityVote service = decorate((account, availability) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("other");
        });

        Assertions.assertThrows(IllegalStateException.class, () -> service.createOrUpdate(accountId, vote));
        Assertions.assertEquals(1, calls.get());
    }
}
