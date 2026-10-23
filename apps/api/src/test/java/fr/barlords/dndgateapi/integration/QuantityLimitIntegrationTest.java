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
 * Own application context with low caps (2 characters, 2 active adventures, 2 spell books, 3 spells, 2 notes of each
 * kind, 3 messages per minute): nothing stops an account from filling the storage and the database otherwise.
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
                "limits.characters-per-player-and-adventure=2",
                "limits.active-adventures-per-master=2",
                "limits.spell-books-per-account=2",
                "limits.spells-per-spell-book=3",
                "limits.adventure-notes-per-adventure=2",
                "limits.historical-notes-per-adventure=2",
                "limits.messages.max=3",
                "limits.messages.window=PT1M"})
@ActiveProfiles("dev")
class QuantityLimitIntegrationTest extends ApiIntegrationTestSupport {

    private void assertConflict(ResponseEntity<String> response) {
        Assertions.assertEquals(409, response.getStatusCode().value(), response.getBody());
        Assertions.assertTrue(response.getHeaders().getContentType().toString().contains("problem+json"));
    }

    private ResponseEntity<String> createAdventureRaw(TestAccount owner) {
        return call(HttpMethod.POST, "/adventures", owner.token(), Map.of(
                "name", "Quest", "description", "A quest", "maxPlayer", 4,
                "visibility", "PUBLIC", "postalCode", "75001", "city", "Paris"));
    }

    // ---- characters ----

    @Test
    void a_player_has_a_limited_number_of_characters_per_adventure() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        UUID otherAdventureId = createAdventure(master, "PUBLIC");
        Assertions.assertEquals(201, createCharacter(master, adventureId, "One").getStatusCode().value());
        Assertions.assertEquals(201, createCharacter(master, adventureId, "Two").getStatusCode().value());

        assertConflict(createCharacter(master, adventureId, "Three"));

        Assertions.assertEquals(201, createCharacter(master, otherAdventureId, "Elsewhere").getStatusCode().value(),
                "the limit is per adventure");
        TestAccount other = registerAndLogin();
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", other.token(), null)
                .getStatusCode().value());
        Assertions.assertEquals(201, createCharacter(other, adventureId, "Other's").getStatusCode().value(),
                "the limit is per player");
    }

    // ---- adventures ----

    @Test
    void a_master_has_a_limited_number_of_active_adventures() {
        TestAccount master = registerAndLogin();
        UUID first = createAdventure(master, "PRIVATE");
        createAdventure(master, "PRIVATE");

        assertConflict(createAdventureRaw(master));

        for (String status : new String[]{"ONGOING", "FINISH"}) {
            Assertions.assertEquals(200, call(HttpMethod.PUT, "/adventures/" + first + "/status", master.token(),
                    Map.of("status", status)).getStatusCode().value());
        }
        Assertions.assertEquals(201, createAdventureRaw(master).getStatusCode().value(), "a finished adventure no longer counts");
        assertConflict(createAdventureRaw(master));
        Assertions.assertEquals(201, createAdventureRaw(registerAndLogin()).getStatusCode().value(), "the limit is per account");
    }

    // ---- spell books and spells ----

    private ResponseEntity<String> createSpellBook(TestAccount account) {
        return call(HttpMethod.POST, "/spellbooks", account.token(), Map.of("name", "Book", "description", "Spells", "ruleset", "dnd5e-2014"));
    }

    private ResponseEntity<String> addSpell(TestAccount account, String bookId, String spell) {
        return call(HttpMethod.POST, "/spellbooks/" + bookId + "/spells", account.token(), Map.of("spellIndex", spell));
    }

    @Test
    void an_account_has_a_limited_number_of_spell_books() {
        TestAccount wizard = registerAndLogin();
        Assertions.assertEquals(201, createSpellBook(wizard).getStatusCode().value());
        Assertions.assertEquals(201, createSpellBook(wizard).getStatusCode().value());

        assertConflict(createSpellBook(wizard));

        Assertions.assertEquals(201, createSpellBook(registerAndLogin()).getStatusCode().value());
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

    // ---- notes ----

    private ResponseEntity<String> adventureNote(TestAccount master, UUID adventureId) {
        return call(HttpMethod.POST, "/adventure-notes", master.token(), Map.of(
                "adventureId", adventureId.toString(), "content", "n", "date", "2026-01-01", "visibility", "PUBLIC"));
    }

    private ResponseEntity<String> historicalNote(TestAccount master, UUID adventureId) {
        return call(HttpMethod.POST, "/historical-notes", master.token(), Map.of(
                "adventureId", adventureId.toString(), "content", "h", "date", "2026-01-01"));
    }

    @Test
    void an_adventure_has_a_limited_number_of_notes_of_each_kind() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        UUID otherAdventureId = createAdventure(master, "PUBLIC");
        Assertions.assertEquals(201, adventureNote(master, adventureId).getStatusCode().value());
        Assertions.assertEquals(201, adventureNote(master, adventureId).getStatusCode().value());
        Assertions.assertEquals(201, historicalNote(master, adventureId).getStatusCode().value());
        Assertions.assertEquals(201, historicalNote(master, adventureId).getStatusCode().value());

        assertConflict(adventureNote(master, adventureId));
        assertConflict(historicalNote(master, adventureId));

        Assertions.assertEquals(201, adventureNote(master, otherAdventureId).getStatusCode().value(), "the limit is per adventure");
        Assertions.assertEquals(201, historicalNote(master, otherAdventureId).getStatusCode().value(), "the limit is per adventure");
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

    @Test
    void an_adventure_without_game_master_does_not_count_in_the_active_limit() {
        TestAccount master = registerAndLogin();
        UUID first = createAdventure(master, "PRIVATE");
        createAdventure(master, "PRIVATE");
        assertConflict(createAdventureRaw(master));

        jdbc.update("update adventure set master_vacant_since = now() where id = ?", first);

        Assertions.assertEquals(201, createAdventureRaw(master).getStatusCode().value());
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
