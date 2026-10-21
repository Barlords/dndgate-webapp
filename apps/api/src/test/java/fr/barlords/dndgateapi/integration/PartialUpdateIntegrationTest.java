package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

class PartialUpdateIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount master;

    private TestAccount attacker;

    private UUID adventureId;

    @BeforeEach
    void fixture() {
        master = registerAndLogin();
        attacker = registerAndLogin();
        adventureId = createAdventure(master, "PUBLIC");
    }

    private int patch(TestAccount account, String path, Map<String, Object> body) {
        return call(HttpMethod.PATCH, path, account.token(), body).getStatusCode().value();
    }

    private String string(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    // ---- a patch only changes what it contains ----

    @Test
    void patching_an_adventure_with_one_field_keeps_the_others() {
        Assertions.assertEquals(201, patch(master, "/adventures/" + adventureId, Map.of("name", "Renamed")));

        Assertions.assertEquals("Renamed", string("select name from adventure where id = ?", adventureId));
        Assertions.assertEquals("A quest", string("select description from adventure where id = ?", adventureId));
        Assertions.assertEquals("Paris", string("select city from adventure where id = ?", adventureId));
        Assertions.assertEquals("75001", string("select postal_code from adventure where id = ?", adventureId));
        Assertions.assertEquals("PUBLIC", string("select visibility from adventure where id = ?", adventureId));
        Assertions.assertEquals(4, jdbc.queryForObject("select max_player from adventure where id = ?", Integer.class, adventureId));
        Assertions.assertEquals("NOT_STARTED", string("select status from adventure where id = ?", adventureId));
    }

    @Test
    void the_postal_code_can_be_patched() {
        Assertions.assertEquals(201, patch(master, "/adventures/" + adventureId, Map.of("postalCode", "69001")));

        Assertions.assertEquals("69001", string("select postal_code from adventure where id = ?", adventureId));
    }

    @Test
    void patching_a_character_with_one_field_keeps_the_others() {
        ResponseEntity<String> created = createCharacter(master, adventureId, "Gandalf");
        UUID characterId = UUID.fromString(body(created).get("id").asString());

        Assertions.assertEquals(201, patch(master, "/game-characters/" + characterId, Map.of("race", "Maia")));

        Assertions.assertEquals("Maia", string("select race from \"character\" where id = ?", characterId));
        Assertions.assertEquals("Gandalf", string("select name from \"character\" where id = ?", characterId));
        Assertions.assertEquals("Wizard", string("select martial_class from \"character\" where id = ?", characterId));
        Assertions.assertEquals("Wise", string("select personality from \"character\" where id = ?", characterId));
        Assertions.assertEquals("Old", string("select background from \"character\" where id = ?", characterId));
    }

    @Test
    void patching_a_note_with_one_field_keeps_the_others() {
        UUID noteId = insertAdventureNote(adventureId, "first", "PRIVATE");
        UUID historyId = insertHistoricalNote(adventureId, "chapter");

        Assertions.assertEquals(201, patch(master, "/adventure-notes/" + noteId, Map.of("content", "second")));
        Assertions.assertEquals(201, patch(master, "/historical-notes/" + historyId, Map.of("content", "chapter two")));

        Assertions.assertEquals("second", string("select content from adventure_note where id = ?", noteId));
        Assertions.assertEquals("PRIVATE", string("select visibility from adventure_note where id = ?", noteId));
        Assertions.assertEquals("2026-01-01", string("select date::text from adventure_note where id = ?", noteId));
        Assertions.assertEquals("chapter two", string("select content from historical_note where id = ?", historyId));
        Assertions.assertEquals("2026-01-01", string("select date::text from historical_note where id = ?", historyId));
    }

    // ---- a note is changed with the right of the adventure it belongs to ----

    @Test
    void the_owner_of_another_adventure_cannot_change_a_note_by_naming_his_own_adventure() {
        UUID attackerAdventure = createAdventure(attacker, "PUBLIC");
        UUID noteId = insertAdventureNote(adventureId, "mine", "PUBLIC");
        UUID historyId = insertHistoricalNote(adventureId, "mine");

        Assertions.assertEquals(403, patch(attacker, "/adventure-notes/" + noteId,
                Map.of("adventureId", attackerAdventure.toString(), "content", "defaced")));
        Assertions.assertEquals(403, patch(attacker, "/historical-notes/" + historyId,
                Map.of("adventureId", attackerAdventure.toString(), "content", "defaced")));

        Assertions.assertEquals("mine", string("select content from adventure_note where id = ?", noteId));
        Assertions.assertEquals("mine", string("select content from historical_note where id = ?", historyId));
    }

    @Test
    void the_owner_changes_a_note_whatever_adventure_the_request_names() {
        UUID noteId = insertAdventureNote(adventureId, "mine", "PUBLIC");

        Assertions.assertEquals(201, patch(master, "/adventure-notes/" + noteId, Map.of("adventureId", UUID.randomUUID().toString(), "content", "edited")));
        Assertions.assertEquals("edited", string("select content from adventure_note where id = ?", noteId));
    }

    private UUID insertAdventureNote(UUID adventure, String content, String visibility) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into adventure_note (id, adventure_id, content, date, visibility) values (?, ?, ?, ?, ?)",
                id, adventure, content, LocalDate.of(2026, 1, 1), visibility);
        return id;
    }

    private UUID insertHistoricalNote(UUID adventure, String content) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into historical_note (id, adventure_id, content, date) values (?, ?, ?, ?)",
                id, adventure, content, LocalDate.of(2026, 1, 1));
        return id;
    }
}
