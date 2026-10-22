package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Two changes of the spells of one book that cross each other: the second request reads the book, the first one
 * changes it and finishes, then the second one saves. Both changes must be in the book (the second request is
 * replayed from a fresh read), whereas saving the stale list used to erase the first change.
 * The same spy bean as {@link AccountAnonymizationAtomicityIntegrationTest}: both classes share one context.
 */
class SpellBookConcurrencyIntegrationTest extends ApiIntegrationTestSupport {

    @MockitoSpyBean
    SpellBookPersistenceSpi spellBooks;

    @AfterEach
    void restore() {
        Mockito.reset(spellBooks);
    }

    private ResponseEntity<String> addSpell(TestAccount owner, UUID spellBookId, String spell) {
        return call(HttpMethod.POST, "/spellbooks/" + spellBookId + "/spells", owner.token(), Map.of("spellIndex", spell));
    }

    private ResponseEntity<String> removeSpell(TestAccount owner, UUID spellBookId, String spell) {
        return call(HttpMethod.DELETE, "/spellbooks/" + spellBookId + "/spells/" + spell, owner.token(), null);
    }

    private List<String> spellsOf(UUID spellBookId) {
        return jdbc.queryForList("select spell_list from spell_book_entity_spell_list where spell_book_entity_id = ? order by spell_list",
                String.class, spellBookId);
    }

    private UUID createSpellBook(TestAccount owner) {
        ResponseEntity<String> response = call(HttpMethod.POST, "/spellbooks", owner.token(), Map.of("name", "Book", "description", "Spells"));
        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
        return UUID.fromString(body(response).get("id").asString());
    }

    /** The first read of the book runs {@code meanwhile} to its end (in another thread) before returning. */
    private void runRightAfterTheNextRead(UUID spellBookId, Supplier<ResponseEntity<String>> meanwhile) {
        AtomicBoolean armed = new AtomicBoolean(true);
        Mockito.doAnswer(invocation -> {
            Object read = invocation.callRealMethod();
            if (armed.getAndSet(false)) {
                ResponseEntity<String> response = CompletableFuture.supplyAsync(meanwhile).get(60, TimeUnit.SECONDS);
                Assertions.assertTrue(response.getStatusCode().is2xxSuccessful(), response.getBody());
            }
            return read;
        }).when(spellBooks).findById(spellBookId);
    }

    @Test
    void two_spells_added_at_the_same_moment_are_both_kept() {
        TestAccount owner = registerAndLogin();
        UUID spellBookId = createSpellBook(owner);
        runRightAfterTheNextRead(spellBookId, () -> addSpell(owner, spellBookId, "shield"));

        Assertions.assertEquals(201, addSpell(owner, spellBookId, "fireball").getStatusCode().value());

        Assertions.assertEquals(List.of("fireball", "shield"), spellsOf(spellBookId));
    }

    @Test
    void two_spells_removed_at_the_same_moment_are_both_removed() {
        TestAccount owner = registerAndLogin();
        UUID spellBookId = createSpellBook(owner);
        for (String spell : List.of("fireball", "shield", "sleep")) {
            Assertions.assertEquals(201, addSpell(owner, spellBookId, spell).getStatusCode().value());
        }
        runRightAfterTheNextRead(spellBookId, () -> removeSpell(owner, spellBookId, "shield"));

        Assertions.assertEquals(204, removeSpell(owner, spellBookId, "fireball").getStatusCode().value());

        Assertions.assertEquals(List.of("sleep"), spellsOf(spellBookId));
    }
}
