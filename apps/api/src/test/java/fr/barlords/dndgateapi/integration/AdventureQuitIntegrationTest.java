package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Leaving an adventure: the characters of the player go back to their reserve, or are deleted when they say so. */
class AdventureQuitIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount masterOn(String planId) {
        TestAccount master = registerAndLogin();
        subscribe(master, planId);
        return master;
    }

    private TestAccount playerOn(UUID adventureId, String planId) {
        TestAccount player = registerAndLogin();
        subscribe(player, planId);
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null)
                .getStatusCode().value());
        return player;
    }

    private String create(TestAccount account, UUID adventureId, String name) {
        ResponseEntity<String> created = createCharacterWithoutPicture(account, adventureId, name);
        Assertions.assertEquals(201, created.getStatusCode().value(), created.getBody());
        return body(created).get("id").asString();
    }

    private ResponseEntity<String> quit(TestAccount player, UUID adventureId, Object body) {
        return call(HttpMethod.DELETE, "/adventures/" + adventureId + "/players/me", player.token(), body);
    }

    private boolean isSeated(TestAccount player, UUID adventureId) {
        return body(call(HttpMethod.GET, "/adventures/" + adventureId, player.token(), null)).get("players").toString()
                .contains(player.id().toString());
    }

    private Map<String, Object> row(String characterId) {
        return jdbc.queryForMap("select adventure_id, active, picture_path from \"character\" where id = ?", UUID.fromString(characterId));
    }

    private int rows(String characterId) {
        return jdbc.queryForObject("select count(*) from \"character\" where id = ?", Integer.class, UUID.fromString(characterId));
    }

    private Map<String, Object> deletion(String characterId, String name) {
        return Map.of("deletions", List.of(Map.of("characterId", characterId, "confirmation", name)));
    }

    @Test
    void quitting_moves_the_characters_to_the_reserve() {
        UUID adventureId = createAdventure(masterOn("test-paid"), "PUBLIC");
        TestAccount player = playerOn(adventureId, "test-default");
        String id = body(createCharacter(player, adventureId, "Gandalf")).get("id").asString();
        String picture = (String) row(id).get("picture_path");
        Assertions.assertNotNull(picture);

        ResponseEntity<String> left = quit(player, adventureId, null);

        Assertions.assertEquals(200, left.getStatusCode().value(), left.getBody());
        Assertions.assertFalse(body(left).get("players").toString().contains(player.id().toString()));
        Map<String, Object> moved = row(id);
        Assertions.assertNull(moved.get("adventure_id"));
        Assertions.assertEquals(false, moved.get("active"));
        Assertions.assertEquals(picture, moved.get("picture_path"), "the picture goes with the character");
    }

    @Test
    void quitting_with_a_full_reserve_is_refused_and_keeps_the_seat() {
        UUID adventureId = createAdventure(masterOn("test-paid"), "PUBLIC");
        TestAccount player = playerOn(adventureId, "test-tight");
        create(player, null, "Already waiting");
        String id = create(player, adventureId, "Gandalf");

        ResponseEntity<String> refused = quit(player, adventureId, null);

        Assertions.assertEquals(409, refused.getStatusCode().value(), refused.getBody());
        Assertions.assertEquals("charactersWithoutAdventure", body(refused).get("limit").asString());
        Assertions.assertTrue(isSeated(player, adventureId), "the seat is kept");
        Assertions.assertEquals(adventureId, row(id).get("adventure_id"));
        Assertions.assertEquals(true, row(id).get("active"));
    }

    @Test
    void quitting_with_confirmed_deletions_deletes_them_even_when_the_reserve_is_full() {
        UUID adventureId = createAdventure(masterOn("test-paid"), "PUBLIC");
        TestAccount player = playerOn(adventureId, "test-tight");
        create(player, null, "Already waiting");
        String deleted = create(player, adventureId, "Boromir");

        ResponseEntity<String> left = quit(player, adventureId, deletion(deleted, "Boromir"));

        Assertions.assertEquals(200, left.getStatusCode().value(), left.getBody());
        Assertions.assertEquals(0, rows(deleted));
        Assertions.assertFalse(body(left).get("players").toString().contains(player.id().toString()));
    }

    @Test
    void quitting_with_confirmed_deletions_deletes_them_and_moves_the_rest() {
        UUID adventureId = createAdventure(masterOn("test-paid"), "PUBLIC");
        TestAccount player = playerOn(adventureId, "test-default");
        String deleted = create(player, adventureId, "Boromir");
        String kept = create(player, adventureId, "Gandalf");

        ResponseEntity<String> left = quit(player, adventureId, deletion(deleted, "Boromir"));

        Assertions.assertEquals(200, left.getStatusCode().value(), left.getBody());
        Assertions.assertEquals(0, rows(deleted));
        Assertions.assertNull(row(kept).get("adventure_id"));
    }

    @Test
    void a_wrong_name_in_a_deletion_refuses_the_whole_quit() {
        UUID adventureId = createAdventure(masterOn("test-paid"), "PUBLIC");
        TestAccount player = playerOn(adventureId, "test-default");
        String deleted = create(player, adventureId, "Boromir");
        String kept = create(player, adventureId, "Gandalf");

        ResponseEntity<String> refused = quit(player, adventureId, deletion(deleted, "Gimli"));

        Assertions.assertEquals(400, refused.getStatusCode().value(), refused.getBody());
        Assertions.assertTrue(isSeated(player, adventureId));
        Assertions.assertEquals(1, rows(deleted));
        Assertions.assertEquals(adventureId, row(kept).get("adventure_id"));
    }

    @Test
    void the_character_of_someone_else_cannot_be_deleted_by_quitting() {
        UUID adventureId = createAdventure(masterOn("test-paid"), "PUBLIC");
        TestAccount player = playerOn(adventureId, "test-default");
        TestAccount other = playerOn(adventureId, "test-default");
        String theirs = create(other, adventureId, "Frodo");

        ResponseEntity<String> refused = quit(player, adventureId, deletion(theirs, "Frodo"));

        Assertions.assertEquals(400, refused.getStatusCode().value(), refused.getBody());
        Assertions.assertTrue(isSeated(player, adventureId));
        Assertions.assertEquals(1, rows(theirs));
    }

    @Test
    void the_same_character_twice_and_an_incomplete_deletion_are_refused() {
        UUID adventureId = createAdventure(masterOn("test-paid"), "PUBLIC");
        TestAccount player = playerOn(adventureId, "test-default");
        String id = create(player, adventureId, "Boromir");

        Map<String, String> entry = Map.of("characterId", id, "confirmation", "Boromir");
        Assertions.assertEquals(400, quit(player, adventureId, Map.of("deletions", List.of(entry, entry))).getStatusCode().value());
        Assertions.assertEquals(400, quit(player, adventureId, Map.of("deletions", List.of(Map.of("characterId", id))))
                .getStatusCode().value());
        Assertions.assertTrue(isSeated(player, adventureId));
        Assertions.assertEquals(1, rows(id));
    }
}
