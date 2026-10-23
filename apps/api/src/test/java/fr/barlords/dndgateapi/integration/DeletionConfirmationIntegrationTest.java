package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every irreversible deletion needs a confirmation, checked by the API and not only by the page:
 * the name of what is deleted, retyped, or the password for an account.
 */
class DeletionConfirmationIntegrationTest extends ApiIntegrationTestSupport {

    private int delete(String path, TestAccount account, Object body) {
        return call(HttpMethod.DELETE, path, account.token(), body).getStatusCode().value();
    }

    private Map<String, Object> confirmation(String value) {
        return Map.of("confirmation", value);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    // ================= account: the password is asked again =================

    @Test
    void deleting_the_account_without_a_confirmation_is_a_bad_request() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(400, delete("/accounts", account, null));
        Assertions.assertEquals(400, delete("/accounts", account, Map.of()));

        Assertions.assertEquals(200, call(HttpMethod.GET, "/accounts/me", account.token(), null).getStatusCode().value());
    }

    @Test
    void a_wrong_password_deletes_nothing_and_does_not_log_the_user_out() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(403, delete("/accounts", account, confirmation("not-my-password")));

        Assertions.assertNull(jdbc.queryForObject("select anonymized_at from accounts where id = ?", Object.class, account.id()));
        Assertions.assertEquals(200, call(HttpMethod.GET, "/accounts/me", account.token(), null).getStatusCode().value(),
                "403 and not 401: the session must stay open");
    }

    @Test
    void a_stolen_access_token_is_not_enough_to_delete_an_account() {
        TestAccount account = registerAndLogin();
        String stolenToken = account.token();

        Assertions.assertEquals(403, call(HttpMethod.DELETE, "/accounts", stolenToken, confirmation("guess")).getStatusCode().value());
        Assertions.assertEquals(403, call(HttpMethod.DELETE, "/accounts", stolenToken, confirmation(account.pseudo())).getStatusCode().value(),
                "the pseudo is not the password");
    }

    @Test
    void wrong_passwords_given_to_confirm_a_deletion_count_toward_the_lockout() {
        TestAccount account = registerAndLogin();

        for (int i = 0; i < 5; i++) {
            Assertions.assertEquals(403, delete("/accounts", account, confirmation("guess-" + i)));
        }

        // locked: even the right password is refused, for the deletion and for the login
        ResponseEntity<String> locked = call(HttpMethod.DELETE, "/accounts", account.token(), confirmation(account.password()));
        Assertions.assertEquals(429, locked.getStatusCode().value());
        Assertions.assertTrue(Long.parseLong(locked.getHeaders().getFirst("Retry-After")) > 0);
        Assertions.assertEquals(429, call(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", account.pseudo(), "password", account.password())).getStatusCode().value());
        Assertions.assertNull(jdbc.queryForObject("select anonymized_at from accounts where id = ?", Object.class, account.id()));
    }

    @Test
    void the_right_password_deletes_the_account() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(204, deleteAccount(account));
        Assertions.assertNotNull(jdbc.queryForObject("select anonymized_at from accounts where id = ?", Object.class, account.id()));
    }

    // ================= adventure: the name is retyped =================

    @Test
    void an_adventure_is_not_deleted_without_its_name() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        String path = "/adventures/" + adventureId;

        Assertions.assertEquals(400, delete(path, master, null));
        Assertions.assertEquals(400, delete(path, master, confirmation("")));
        Assertions.assertEquals(400, delete(path, master, confirmation("quest")), "the case matters");
        Assertions.assertEquals(400, delete(path, master, confirmation("Another quest")));

        Assertions.assertEquals(1, count("select count(*) from adventure where id = ?", adventureId));
    }

    @Test
    void the_name_of_an_adventure_is_compared_without_the_spaces_around_it_and_whatever_the_encoding_of_accents() {
        TestAccount master = registerAndLogin();
        ResponseEntity<String> created = call(HttpMethod.POST, "/adventures", master.token(), Map.of(
                "name", "Épée du soir", "description", "d", "maxPlayer", 4, "visibility", "PUBLIC",
                "postalCode", "75001", "city", "Paris"));
        UUID adventureId = UUID.fromString(body(created).get("id").asString());

        // typed with an "E" followed by a combining accent, and spaces around
        Assertions.assertEquals(204, delete("/adventures/" + adventureId, master, confirmation("  Épée du soir ")));
    }

    @Test
    void someone_who_is_not_the_owner_cannot_delete_an_adventure_even_with_its_name() {
        TestAccount master = registerAndLogin();
        TestAccount player = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null);

        Assertions.assertEquals(403, delete("/adventures/" + adventureId, player, confirmation("Quest")));
        Assertions.assertEquals(1, count("select count(*) from adventure where id = ?", adventureId));
    }

    @Test
    void an_adventure_in_progress_cannot_be_deleted_until_it_is_finished() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        call(HttpMethod.PUT, "/adventures/" + adventureId + "/status", master.token(), Map.of("status", "ONGOING"));

        Assertions.assertEquals(409, delete("/adventures/" + adventureId, master, confirmation("Quest")));
        Assertions.assertEquals(1, count("select count(*) from adventure where id = ?", adventureId));

        call(HttpMethod.PUT, "/adventures/" + adventureId + "/status", master.token(), Map.of("status", "FINISH"));
        Assertions.assertEquals(204, delete("/adventures/" + adventureId, master, confirmation("Quest")));
    }

    @Test
    void deleting_an_adventure_removes_everything_that_belongs_to_it() throws Exception {
        TestAccount master = registerAndLogin();
        TestAccount player = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null);

        UUID characterId = UUID.fromString(body(createCharacter(player, adventureId, "Gandalf")).get("id").asString());
        String picturePath = jdbc.queryForObject("select picture_path from \"character\" where id = ?", String.class, characterId);
        call(HttpMethod.POST, "/adventure-notes", master.token(), Map.of(
                "adventureId", adventureId.toString(), "content", "secret", "date", "2026-01-01", "visibility", "PRIVATE"));
        call(HttpMethod.POST, "/historical-notes", master.token(), Map.of(
                "adventureId", adventureId.toString(), "content", "history", "date", "2026-01-01"));
        call(HttpMethod.POST, "/messages", player.token(), Map.of(
                "adventureId", adventureId.toString(), "content", "hello"));
        UUID planningId = UUID.fromString(body(call(HttpMethod.POST, "/planning", master.token(), Map.of(
                "adventureId", adventureId.toString(), "propositionDates", List.of("2026-02-01", "2026-02-08")))).get("id").asString());
        call(HttpMethod.PUT, "/planning/availability", player.token(), Map.of(
                "planningId", planningId.toString(), "propositionDates", List.of("2026-02-01")));
        UUID voteId = jdbc.queryForObject("select id from availability where planning_id = ?", UUID.class, planningId);

        // everything is there
        Assertions.assertEquals(1, count("select count(*) from \"character\" where adventure_id = ?", adventureId));
        Assertions.assertEquals(1, count("select count(*) from adventure_note where adventure_id = ?", adventureId));
        Assertions.assertEquals(1, count("select count(*) from historical_note where adventure_id = ?", adventureId));
        Assertions.assertEquals(1, count("select count(*) from message where adventure_id = ?", adventureId));
        Assertions.assertEquals(1, count("select count(*) from availability where planning_id = ?", planningId));

        Assertions.assertEquals(204, delete("/adventures/" + adventureId, master, confirmation("Quest")));

        Assertions.assertEquals(0, count("select count(*) from adventure where id = ?", adventureId));
        Assertions.assertEquals(0, count("select count(*) from adventure_players where adventure_id = ?", adventureId));
        Assertions.assertEquals(0, count("select count(*) from \"character\" where adventure_id = ?", adventureId));
        Assertions.assertEquals(0, count("select count(*) from adventure_note where adventure_id = ?", adventureId));
        Assertions.assertEquals(0, count("select count(*) from historical_note where adventure_id = ?", adventureId));
        Assertions.assertEquals(0, count("select count(*) from message where adventure_id = ?", adventureId));
        Assertions.assertEquals(0, count("select count(*) from planning where adventure_id = ?", adventureId));
        Assertions.assertEquals(0, count("select count(*) from propositions where planning_id = ?", planningId));
        Assertions.assertEquals(0, count("select count(*) from availability where planning_id = ?", planningId));
        Assertions.assertEquals(0, count("select count(*) from availability_dates where availability_id = ?", voteId));
        Mockito.verify(minioSpi).deleteAfterCommit(picturePath);

        // the accounts themselves are untouched
        Assertions.assertEquals(200, call(HttpMethod.GET, "/accounts/me", player.token(), null).getStatusCode().value());
    }

    @Test
    void the_other_adventures_are_left_alone() {
        TestAccount master = registerAndLogin();
        UUID doomed = createAdventure(master, "PUBLIC");
        UUID kept = createAdventure(master, "PUBLIC");
        call(HttpMethod.POST, "/messages", master.token(), Map.of(
                "adventureId", kept.toString(), "content", "stay"));

        Assertions.assertEquals(204, delete("/adventures/" + doomed, master, confirmation("Quest")));

        Assertions.assertEquals(1, count("select count(*) from adventure where id = ?", kept));
        Assertions.assertEquals(1, count("select count(*) from message where adventure_id = ?", kept));
    }

    // ================= character =================

    @Test
    void a_character_is_deleted_with_its_name_and_its_picture() throws Exception {
        TestAccount master = registerAndLogin();
        TestAccount player = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null);
        UUID characterId = UUID.fromString(body(createCharacter(player, adventureId, "Gandalf")).get("id").asString());
        String picturePath = jdbc.queryForObject("select picture_path from \"character\" where id = ?", String.class, characterId);
        String path = "/game-characters/" + characterId;

        Assertions.assertEquals(400, delete(path, player, null));
        Assertions.assertEquals(400, delete(path, player, confirmation("gandalf")));
        Assertions.assertEquals(403, delete(path, master, confirmation("Gandalf")), "only its owner can delete it");
        Assertions.assertEquals(1, count("select count(*) from \"character\" where id = ?", characterId));
        Mockito.verify(minioSpi, Mockito.never()).deleteAfterCommit(Mockito.anyString());

        Assertions.assertEquals(204, delete(path, player, confirmation("Gandalf")));

        Assertions.assertEquals(0, count("select count(*) from \"character\" where id = ?", characterId));
        Mockito.verify(minioSpi).deleteAfterCommit(picturePath);
    }

    // ================= spell book =================

    @Test
    void a_spell_book_is_deleted_with_its_name_by_its_owner_only() {
        TestAccount owner = registerAndLogin();
        TestAccount other = registerAndLogin();
        UUID bookId = UUID.fromString(body(call(HttpMethod.POST, "/spellbooks", owner.token(),
                Map.of("name", "Fireballs", "description", "d", "ruleset", "dnd5e-2014"))).get("id").asString());
        String path = "/spellbooks/" + bookId;

        Assertions.assertEquals(400, delete(path, owner, null));
        Assertions.assertEquals(400, delete(path, owner, confirmation("Fireball")));
        Assertions.assertEquals(403, delete(path, other, confirmation("Fireballs")));
        Assertions.assertEquals(1, count("select count(*) from spellbook where id = ?", bookId));

        Assertions.assertEquals(204, delete(path, owner, confirmation("Fireballs")));
        Assertions.assertEquals(0, count("select count(*) from spellbook where id = ?", bookId));
    }

    // ================= notes: no name, an explicit confirmation =================

    @Test
    void a_note_of_the_game_master_needs_an_explicit_confirmation() {
        TestAccount master = registerAndLogin();
        TestAccount player = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null);
        UUID noteId = UUID.fromString(body(call(HttpMethod.POST, "/adventure-notes", master.token(), Map.of(
                "adventureId", adventureId.toString(), "content", "n", "date", "2026-01-01", "visibility", "PUBLIC"))).get("id").asString());
        String path = "/adventure-notes/" + noteId;

        Assertions.assertEquals(400, delete(path, master, null));
        Assertions.assertEquals(400, delete(path, master, Map.of("confirm", false)));
        Assertions.assertEquals(400, delete(path, master, Map.of("confirm", "yes")));
        Assertions.assertEquals(403, delete(path, player, Map.of("confirm", true)), "only the game master can");
        Assertions.assertEquals(1, count("select count(*) from adventure_note where id = ?", noteId));

        Assertions.assertEquals(204, delete(path, master, Map.of("confirm", true)));
        Assertions.assertEquals(0, count("select count(*) from adventure_note where id = ?", noteId));
    }

    @Test
    void a_historical_note_needs_an_explicit_confirmation_too() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        UUID noteId = UUID.fromString(body(call(HttpMethod.POST, "/historical-notes", master.token(), Map.of(
                "adventureId", adventureId.toString(), "content", "h", "date", "2026-01-01"))).get("id").asString());
        String path = "/historical-notes/" + noteId;

        Assertions.assertEquals(400, delete(path, master, null));
        Assertions.assertEquals(400, delete(path, master, Map.of("confirm", false)));
        Assertions.assertEquals(1, count("select count(*) from historical_note where id = ?", noteId));

        Assertions.assertEquals(204, delete(path, master, Map.of("confirm", true)));
        Assertions.assertEquals(0, count("select count(*) from historical_note where id = ?", noteId));
    }
}
