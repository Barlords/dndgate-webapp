package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

/**
 * An account is never left half anonymized: when a step fails, nothing is changed.
 */
class AccountAnonymizationAtomicityIntegrationTest extends ApiIntegrationTestSupport {

    @MockitoSpyBean
    SpellBookPersistenceSpi spellBooks;

    @Test
    void a_failure_in_the_middle_leaves_everything_as_it_was() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        call(HttpMethod.POST, "/spellbooks", master.token(), Map.of("name", "My book", "description", "Fireballs"));

        // the adventure is aborted first, the spell books are deleted after: make that second step fail
        Mockito.doThrow(new IllegalStateException("simulated failure")).when(spellBooks).deleteById(any());

        Assertions.assertEquals(500, deleteAccount(master));

        Assertions.assertEquals("NOT_STARTED", jdbc.queryForObject("select status from adventure where id = ?", String.class, adventureId),
                "the adventure must not stay aborted");
        Assertions.assertEquals("PUBLIC", jdbc.queryForObject("select visibility from adventure where id = ?", String.class, adventureId));
        Assertions.assertNull(jdbc.queryForObject("select anonymized_at from accounts where id = ?", Object.class, master.id()));
        Assertions.assertEquals(master.pseudo(), jdbc.queryForObject("select pseudo from accounts where id = ?", String.class, master.id()));
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from spellbook where account_id = ?", Integer.class, master.id()));
        Assertions.assertEquals(200, call(HttpMethod.GET, "/accounts/me", master.token(), null).getStatusCode().value(),
                "the sessions must not have been ended");
    }
}
