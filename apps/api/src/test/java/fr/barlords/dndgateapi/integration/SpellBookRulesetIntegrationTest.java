package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * A spell book belongs to a ruleset chosen at creation: it is required, well formed, and never changed by a patch.
 */
class SpellBookRulesetIntegrationTest extends ApiIntegrationTestSupport {

    private ResponseEntity<String> create(TestAccount account, Map<String, Object> body) {
        return call(HttpMethod.POST, "/spellbooks", account.token(), body);
    }

    @Test
    void a_spell_book_is_created_with_its_ruleset() {
        TestAccount account = registerAndLogin();

        ResponseEntity<String> response = create(account,
                Map.of("name", "Book", "description", "d", "ruleset", "dnd5e-2024"));

        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals("dnd5e-2024", body(response).get("ruleset").asText());
    }

    @Test
    void a_spell_book_without_ruleset_is_a_400() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(400,
                create(account, Map.of("name", "Book", "description", "d")).getStatusCode().value());
    }

    @Test
    void a_ruleset_that_is_not_an_identifier_is_a_400() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(400, create(account,
                Map.of("name", "Book", "description", "d", "ruleset", "DnD 5e")).getStatusCode().value());
    }

    @Test
    void patching_a_spell_book_keeps_its_ruleset() {
        TestAccount account = registerAndLogin();
        String id = body(create(account,
                Map.of("name", "Book", "description", "d", "ruleset", "dnd5e-2024"))).get("id").asText();

        ResponseEntity<String> patched = call(HttpMethod.PATCH, "/spellbooks/" + id, account.token(),
                Map.of("name", "Renamed", "ruleset", "dnd5e-2014"));

        Assertions.assertEquals(200, patched.getStatusCode().value(), patched.getBody());
        Assertions.assertEquals("Renamed", body(patched).get("name").asText());
        Assertions.assertEquals("dnd5e-2024", body(patched).get("ruleset").asText());
        Assertions.assertEquals("dnd5e-2024", body(call(HttpMethod.GET, "/spellbooks/" + id, account.token(), null))
                .get("ruleset").asText());
    }
}
