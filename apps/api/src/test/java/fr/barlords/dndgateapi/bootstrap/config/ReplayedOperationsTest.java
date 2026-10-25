package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.functional.exception.ConcurrentUpdateException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureConcurrentModificationException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureUpdaterApi;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.functional.model.Upserted;
import fr.barlords.dndgateapi.domain.functional.model.planning.Availability;
import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The "read, then modify" operations are replayed from a fresh read when someone changed the same row meanwhile;
 * the other ones answer the conflict.
 */
class ReplayedOperationsTest {

    private final UUID accountId = UUID.randomUUID();

    private final UUID spellBookId = UUID.randomUUID();

    private final RetryingTransaction transaction = transaction();

    private static RetryingTransaction transaction() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new RetryingTransaction(manager);
    }

    private static ConcurrentUpdateException conflict() {
        return new ConcurrentUpdateException(new IllegalStateException("stale"));
    }

    // ---- spells ----

    @Test
    void a_spell_addition_met_by_a_concurrent_change_is_replayed() {
        AtomicInteger calls = new AtomicInteger();
        SpellBook book = SpellBook.builder().build();
        TransactionalSpellWriter writer = new TransactionalSpellWriter((account, id, spell) -> {
            if (calls.incrementAndGet() == 1) {
                throw conflict();
            }
            return book;
        }, transaction);

        Assertions.assertSame(book, writer.addSpell(accountId, spellBookId, "fireball"));
        Assertions.assertEquals(2, calls.get());
    }

    @Test
    void a_spell_addition_failing_otherwise_is_not_replayed() {
        AtomicInteger calls = new AtomicInteger();
        TransactionalSpellWriter writer = new TransactionalSpellWriter((account, id, spell) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("other");
        }, transaction);

        Assertions.assertThrows(IllegalStateException.class, () -> writer.addSpell(accountId, spellBookId, "fireball"));
        Assertions.assertEquals(1, calls.get());
    }

    @Test
    void a_spell_removal_met_by_a_concurrent_change_is_replayed() {
        AtomicInteger calls = new AtomicInteger();
        TransactionalSpellDeleter deleter = new TransactionalSpellDeleter((account, id, spell) -> {
            if (calls.incrementAndGet() == 1) {
                throw conflict();
            }
        }, transaction);

        deleter.deleteSpell(accountId, spellBookId, "fireball");

        Assertions.assertEquals(2, calls.get());
    }

    @Test
    void a_conflict_that_never_goes_away_is_finally_reported() {
        AtomicInteger calls = new AtomicInteger();
        TransactionalSpellDeleter deleter = new TransactionalSpellDeleter((account, id, spell) -> {
            calls.incrementAndGet();
            throw conflict();
        }, transaction);

        Assertions.assertThrows(ConcurrentUpdateException.class, () -> deleter.deleteSpell(accountId, spellBookId, "fireball"));
        Assertions.assertEquals(10, calls.get());
    }

    // ---- availability vote: both kinds of conflict ----

    @Test
    void a_vote_met_by_a_concurrent_change_is_replayed() {
        AtomicInteger calls = new AtomicInteger();
        Availability vote = Availability.builder().build();
        TransactionalAvailabilityVote voting = new TransactionalAvailabilityVote((account, availability) -> {
            if (calls.incrementAndGet() == 1) {
                throw conflict();
            }
            return Upserted.created(availability);
        }, transaction);

        Assertions.assertSame(vote, voting.createOrUpdate(accountId, vote).value());
        Assertions.assertEquals(2, calls.get());
    }

    // ---- planning: only the creation race is replayed ----

    @Test
    void two_first_plannings_of_an_adventure_are_replayed_into_an_update() {
        AtomicInteger calls = new AtomicInteger();
        Planning planning = Planning.builder().build();
        TransactionalPlanningUpdater updater = new TransactionalPlanningUpdater((account, sent) -> {
            if (calls.incrementAndGet() == 1) {
                throw new DataIntegrityViolationException("duplicate key");
            }
            return Upserted.updated(sent);
        }, transaction);

        Assertions.assertSame(planning, updater.createOrUpdate(accountId, planning).value());
        Assertions.assertEquals(2, calls.get());
    }

    @Test
    void a_planning_changed_meanwhile_is_reported_and_not_replayed() {
        AtomicInteger calls = new AtomicInteger();
        TransactionalPlanningUpdater updater = new TransactionalPlanningUpdater((account, sent) -> {
            calls.incrementAndGet();
            throw conflict();
        }, transaction);

        Planning planning = Planning.builder().build();
        Assertions.assertThrows(ConcurrentUpdateException.class, () -> updater.createOrUpdate(accountId, planning));
        Assertions.assertEquals(1, calls.get());
    }

    // ---- leaving an adventure ----

    @Test
    void leaving_an_adventure_met_by_a_concurrent_change_is_replayed_from_a_fresh_attempt() {
        AdventureUpdaterApi updater = mock(AdventureUpdaterApi.class);
        UUID adventureId = UUID.randomUUID();
        Adventure left = Adventure.builder().build();
        when(updater.quitAdventure(adventureId, accountId, Map.of()))
                .thenThrow(new AdventureConcurrentModificationException(null))
                .thenReturn(left);

        Assertions.assertSame(left, new TransactionalAdventureQuitter(updater, transaction).quitAdventure(adventureId, accountId, Map.of()));
        verify(updater, times(2)).quitAdventure(adventureId, accountId, Map.of());
    }

    @Test
    void the_other_operations_of_the_adventure_updater_pass_through_untouched() {
        AdventureUpdaterApi updater = mock(AdventureUpdaterApi.class);
        TransactionalAdventureQuitter quitter = new TransactionalAdventureQuitter(updater, transaction);
        UUID id = UUID.randomUUID();
        Adventure adventure = Adventure.builder().build();

        quitter.update(accountId, adventure);
        quitter.updateVisibility(accountId, id, AdventureVisibility.PRIVATE);
        quitter.updateStatus(accountId, id, AdventureStatus.ONGOING);
        quitter.joinAdventure(id, accountId);
        quitter.joinAdventureByCode(accountId, "code");
        quitter.sendInvitationCode(accountId, id, "pseudo");

        verify(updater).update(accountId, adventure);
        verify(updater).updateVisibility(accountId, id, AdventureVisibility.PRIVATE);
        verify(updater).updateStatus(accountId, id, AdventureStatus.ONGOING);
        verify(updater).joinAdventure(id, accountId);
        verify(updater).joinAdventureByCode(accountId, "code");
        verify(updater).sendInvitationCode(accountId, id, "pseudo");
    }
}
