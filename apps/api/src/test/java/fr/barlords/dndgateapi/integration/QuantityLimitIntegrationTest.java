package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.bootstrap.DndGateApiApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

/**
 * Own application context with low technical ceilings (3 spells per book, 3 messages per minute): nothing stops an account from filling the storage and the database otherwise.
 * The quotas that depend on the plan are in {@link PlanLimitIntegrationTest}.
 */
@SpringBootTest(
        classes = DndGateApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "security.jwt.secret=integration-test-secret-integration-test-secret",
                "management.server.port=0",
                "security.rate-limit.login.max=1000000",
                "security.rate-limit.refresh.max=1000000",
                "security.rate-limit.register.max=1000000",
                "security.rate-limit.verify.max=1000000",
                "security.rate-limit.resend.max=1000000",
                "plans.file=classpath:plans/test-plans.json",
                "limits.spells-per-spell-book=3",
                "limits.messages.max=3",
                "limits.messages.window=PT1M"})
@ActiveProfiles("dev")
class QuantityLimitIntegrationTest extends ApiIntegrationTestSupport {

    private void assertConflict(ResponseEntity<String> response) {
        Assertions.assertEquals(409, response.getStatusCode().value(), response.getBody());
        Assertions.assertTrue(response.getHeaders().getContentType().toString().contains("problem+json"));
    }

    // ---- spell books and spells ----

    private ResponseEntity<String> createSpellBook(TestAccount account) {
        return call(HttpMethod.POST, "/spellbooks", account.token(), Map.of("name", "Book", "description", "Spells", "ruleset", "dnd5e-2014"));
    }

    private ResponseEntity<String> addSpell(TestAccount account, String bookId, String spell) {
        return call(HttpMethod.POST, "/spellbooks/" + bookId + "/spells", account.token(), Map.of("spellIndex", spell));
    }

    @Test
    void a_spell_book_has_a_limited_number_of_spells_but_a_known_spell_can_be_added_again() {
        TestAccount wizard = registerAndLogin();
        String bookId = body(createSpellBook(wizard)).get("id").asString();
        for (String spell : new String[]{"fireball", "shield", "light"}) {
            Assertions.assertEquals(201, addSpell(wizard, bookId, spell).getStatusCode().value());
        }

        assertConflict(addSpell(wizard, bookId, "magic-missile"));

        Assertions.assertEquals(201, addSpell(wizard, bookId, "fireball").getStatusCode().value());
        Assertions.assertEquals(3, jdbc.queryForObject("select count(*) from spellbook_spells where spellbook_id = ?::uuid",
                Integer.class, bookId));
    }

    // ---- messages ----

    private ResponseEntity<String> message(TestAccount account, UUID adventureId) {
        return call(HttpMethod.POST, "/messages", account.token(), Map.of("adventureId", adventureId.toString(), "content", "hello"));
    }

    @Test
    void an_account_can_only_write_a_few_messages_per_minute() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        for (int i = 0; i < 3; i++) {
            Assertions.assertEquals(201, message(master, adventureId).getStatusCode().value());
        }

        ResponseEntity<String> refused = message(master, adventureId);

        Assertions.assertEquals(429, refused.getStatusCode().value(), refused.getBody());
        Assertions.assertTrue(refused.getHeaders().getContentType().toString().contains("problem+json"));
        long retryAfter = Long.parseLong(refused.getHeaders().getFirst("Retry-After"));
        Assertions.assertTrue(retryAfter >= 1 && retryAfter <= 60, "Retry-After " + retryAfter);
        Assertions.assertEquals(3, jdbc.queryForObject("select count(*) from message where sender_id = ?", Integer.class, master.id()));

        TestAccount other = registerAndLogin();
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", other.token(), null)
                .getStatusCode().value());
        Assertions.assertEquals(201, message(other, adventureId).getStatusCode().value(), "the limit is per account");
    }

    @Test
    void old_messages_leave_the_window() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        for (int i = 0; i < 3; i++) {
            Assertions.assertEquals(201, message(master, adventureId).getStatusCode().value());
        }
        Assertions.assertEquals(429, message(master, adventureId).getStatusCode().value());

        jdbc.update("update message set send_date = send_date - interval '2 minutes' where sender_id = ?", master.id());

        Assertions.assertEquals(201, message(master, adventureId).getStatusCode().value());
    }

    // ---- import ----

    private ResponseEntity<String> importSpellBook(TestAccount account, int spellCount) {
        String spells = java.util.stream.IntStream.range(0, spellCount)
                .mapToObj(i -> "\"spell-" + i + "\"").collect(java.util.stream.Collectors.joining(","));
        byte[] file = ("{\"name\":\"Imported\",\"description\":\"d\",\"spellList\":[" + spells + "]}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        org.springframework.util.LinkedMultiValueMap<String, Object> parts = new org.springframework.util.LinkedMultiValueMap<>();
        parts.add("file", new org.springframework.core.io.ByteArrayResource(file) {
            @Override
            public String getFilename() {
                return "book.json";
            }
        });
        return client().post().uri("/spellbooks/import-file")
                .headers(headers -> headers.setBearerAuth(account.token()))
                .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve().toEntity(String.class);
    }

    @Test
    void an_imported_spell_book_is_limited_in_spells_like_any_other() {
        TestAccount wizard = registerAndLogin();

        assertConflict(importSpellBook(wizard, 4));
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from spellbook where account_id = ?", Integer.class, wizard.id()));

        Assertions.assertEquals(201, importSpellBook(wizard, 3).getStatusCode().value());
    }
}
