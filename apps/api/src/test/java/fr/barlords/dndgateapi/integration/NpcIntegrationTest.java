package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/** The non-player characters of the game master: private until the master shows them, managed by the current master. */
class NpcIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount master;

    private TestAccount player;

    private TestAccount stranger;

    private UUID adventureId;

    @BeforeEach
    void fixture() {
        reset(minioSpi);
        master = registerAndLogin();
        player = registerAndLogin();
        stranger = registerAndLogin();
        adventureId = createAdventure(master, "PUBLIC");
        join(player, adventureId);
    }

    private void join(TestAccount account, UUID adventure) {
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventure + "/join", account.token(), null)
                .getStatusCode().value());
    }

    private String npc(String name) {
        ResponseEntity<String> created = createCharacterWithoutPicture(master, adventureId, name);
        Assertions.assertEquals(201, created.getStatusCode().value(), created.getBody());
        return body(created).get("id").asString();
    }

    private int status(TestAccount account, HttpMethod method, String path, Object body) {
        return call(method, path, account.token(), body).getStatusCode().value();
    }

    private int setVisibility(TestAccount account, String id, String visibility) {
        return status(account, HttpMethod.PUT, "/game-characters/" + id + "/visibility", Map.of("visibility", visibility));
    }

    private int listedInAdventure(TestAccount account, String id) {
        JsonNode list = body(call(HttpMethod.GET, "/game-characters?adventureId=" + adventureId, account.token(), null));
        int found = 0;
        for (JsonNode character : list) {
            if (id.equals(character.get("id").asString())) {
                found++;
            }
        }
        return found;
    }

    @Test
    void a_master_creates_a_private_npc() {
        ResponseEntity<String> created = createCharacterWithoutPicture(master, adventureId, "Innkeeper");

        Assertions.assertEquals(201, created.getStatusCode().value(), created.getBody());
        JsonNode npc = body(created);
        Assertions.assertTrue(npc.get("npc").asBoolean());
        Assertions.assertEquals("PRIVATE", npc.get("visibility").asString());
        Assertions.assertFalse(npc.get("active").asBoolean());
        Assertions.assertEquals(master.id().toString(), npc.get("ownerId").asString());

        JsonNode played = body(createCharacterWithoutPicture(player, adventureId, "Gandalf"));
        Assertions.assertFalse(played.get("npc").asBoolean());
        Assertions.assertEquals("PUBLIC", played.get("visibility").asString());
        Assertions.assertTrue(played.get("active").asBoolean());
    }

    @Test
    void the_npc_limit_comes_from_the_master_plan() {
        TestAccount tight = registerAndLogin();
        subscribe(tight, "test-tight");
        UUID tightAdventure = createAdventure(tight, "PUBLIC");
        Assertions.assertEquals(201, createCharacterWithoutPicture(tight, tightAdventure, "One").getStatusCode().value());

        ResponseEntity<String> refused = createCharacterWithoutPicture(tight, tightAdventure, "Two");

        Assertions.assertEquals(409, refused.getStatusCode().value(), refused.getBody());
        Assertions.assertEquals("npcsPerAdventure", body(refused).get("limit").asString());
        TestAccount roomy = registerAndLogin();
        subscribe(roomy, "test-paid");
        UUID roomyAdventure = createAdventure(roomy, "PUBLIC");
        Assertions.assertEquals(201, createCharacterWithoutPicture(roomy, roomyAdventure, "One").getStatusCode().value());
        Assertions.assertEquals(201, createCharacterWithoutPicture(roomy, roomyAdventure, "Two").getStatusCode().value());
    }

    @Test
    void the_npcs_are_counted_in_the_entitlements_of_the_adventure() {
        npc("Innkeeper");

        JsonNode limits = body(call(HttpMethod.GET, "/adventures/" + adventureId + "/entitlements", master.token(), null)).get("limits");

        Assertions.assertEquals(1, limits.get("npcsPerAdventure").get("used").asInt());
        Assertions.assertEquals(0, limits.get("charactersPerPlayerAndAdventure").get("used").asInt());
    }

    @Test
    void a_player_never_reads_a_private_npc_by_list_by_id_or_by_picture() throws Exception {
        String id = body(createCharacter(master, adventureId, "Innkeeper")).get("id").asString();
        String path = jdbc.queryForObject("select picture_path from \"character\" where id = ?", String.class, UUID.fromString(id));
        when(minioSpi.downloadFile(path)).thenAnswer(invocation -> ByteBuffer.wrap(PNG));

        Assertions.assertEquals(1, listedInAdventure(master, id));
        Assertions.assertEquals(200, status(master, HttpMethod.GET, "/game-characters/" + id, null));
        Assertions.assertEquals(200, status(master, HttpMethod.GET, "/game-characters/" + id + "/picture", null));
        for (TestAccount reader : new TestAccount[]{player, stranger}) {
            Assertions.assertEquals(404, status(reader, HttpMethod.GET, "/game-characters/" + id, null));
            Assertions.assertEquals(404, status(reader, HttpMethod.GET, "/game-characters/" + id + "/picture", null));
        }
        Assertions.assertEquals(0, listedInAdventure(player, id));
        Assertions.assertEquals(404, status(player, HttpMethod.PATCH, "/game-characters/" + id, Map.of("race", "Elf")));
        Assertions.assertEquals(404, status(player, HttpMethod.PUT, "/game-characters/" + id + "/visibility", Map.of("visibility", "PUBLIC")));
        Assertions.assertEquals(404, status(player, HttpMethod.DELETE, "/game-characters/" + id, Map.of("confirmation", "Innkeeper")));
    }

    @Test
    void a_public_npc_is_read_only_for_players() {
        String id = npc("Innkeeper");
        Assertions.assertEquals(200, setVisibility(master, id, "PUBLIC"));

        Assertions.assertEquals(200, status(player, HttpMethod.GET, "/game-characters/" + id, null));
        Assertions.assertEquals(1, listedInAdventure(player, id));
        Assertions.assertEquals(403, status(stranger, HttpMethod.GET, "/game-characters/" + id, null), "outside the adventure");
        Assertions.assertEquals(403, status(player, HttpMethod.PATCH, "/game-characters/" + id, Map.of("race", "Elf")));
        Assertions.assertEquals(403, setVisibility(player, id, "PRIVATE"));
        Assertions.assertEquals(403, status(player, HttpMethod.DELETE, "/game-characters/" + id, Map.of("confirmation", "Innkeeper")));
        Assertions.assertEquals("PUBLIC", jdbc.queryForObject("select visibility from \"character\" where id = ?", String.class, UUID.fromString(id)));
    }

    @Test
    void one_call_makes_an_npc_public_or_private() {
        String id = npc("Innkeeper");

        Assertions.assertEquals(404, status(player, HttpMethod.GET, "/game-characters/" + id, null));
        ResponseEntity<String> shown = call(HttpMethod.PUT, "/game-characters/" + id + "/visibility", master.token(),
                Map.of("visibility", "PUBLIC"));
        Assertions.assertEquals(200, shown.getStatusCode().value(), shown.getBody());
        Assertions.assertEquals("PUBLIC", body(shown).get("visibility").asString());
        Assertions.assertEquals(200, status(player, HttpMethod.GET, "/game-characters/" + id, null));

        Assertions.assertEquals(200, setVisibility(master, id, "PRIVATE"));
        Assertions.assertEquals(404, status(player, HttpMethod.GET, "/game-characters/" + id, null));
        Assertions.assertEquals(400, status(master, HttpMethod.PUT, "/game-characters/" + id + "/visibility", Map.of()));
        Assertions.assertEquals(400, setVisibility(master, id, "SECRET"));
    }

    @Test
    void the_visibility_of_a_player_character_cannot_be_changed() {
        String id = body(createCharacterWithoutPicture(player, adventureId, "Gandalf")).get("id").asString();

        Assertions.assertEquals(409, setVisibility(player, id, "PRIVATE"));
        Assertions.assertEquals("PUBLIC", jdbc.queryForObject("select visibility from \"character\" where id = ?", String.class, UUID.fromString(id)));
    }

    @Test
    void the_master_updates_and_deletes_an_npc_with_the_name() {
        String id = npc("Innkeeper");

        Assertions.assertEquals(200, status(master, HttpMethod.PATCH, "/game-characters/" + id, Map.of("race", "Dwarf")));
        Assertions.assertEquals(400, status(master, HttpMethod.DELETE, "/game-characters/" + id, Map.of("confirmation", "innkeeper")));
        Assertions.assertEquals(204, status(master, HttpMethod.DELETE, "/game-characters/" + id, Map.of("confirmation", "Innkeeper")));
        Assertions.assertEquals(404, status(master, HttpMethod.GET, "/game-characters/" + id, null));
    }

    @Test
    void the_new_master_manages_the_npcs_after_a_transfer() {
        String id = npc("Innkeeper");
        String code = body(call(HttpMethod.POST, "/adventures/" + adventureId + "/master/invitation", master.token(), null)).get("code").asString();
        Assertions.assertEquals(200, status(stranger, HttpMethod.POST, "/adventures/master/accept", Map.of("code", code)));

        // The former master owns it but no longer manages it, nor even knows of it while it is private
        Assertions.assertEquals(404, status(master, HttpMethod.GET, "/game-characters/" + id, null));
        Assertions.assertEquals(404, status(master, HttpMethod.PATCH, "/game-characters/" + id, Map.of("race", "Elf")));
        Assertions.assertEquals(200, status(stranger, HttpMethod.GET, "/game-characters/" + id, null));
        Assertions.assertEquals(200, status(stranger, HttpMethod.PATCH, "/game-characters/" + id, Map.of("race", "Elf")));
        Assertions.assertEquals(200, setVisibility(stranger, id, "PUBLIC"));
        Assertions.assertEquals(403, setVisibility(master, id, "PRIVATE"), "now a public NPC of somebody else's adventure");
        Assertions.assertEquals(204, status(stranger, HttpMethod.DELETE, "/game-characters/" + id, Map.of("confirmation", "Innkeeper")));
    }

    @Test
    void a_former_master_who_quits_leaves_the_npcs_in_the_adventure() {
        String id = npc("Innkeeper");
        String code = body(call(HttpMethod.POST, "/adventures/" + adventureId + "/master/invitation", master.token(), null)).get("code").asString();
        Assertions.assertEquals(200, status(stranger, HttpMethod.POST, "/adventures/master/accept", Map.of("code", code)));

        Assertions.assertEquals(200, status(master, HttpMethod.DELETE, "/adventures/" + adventureId + "/players/me", null));

        Assertions.assertEquals(adventureId, jdbc.queryForObject("select adventure_id from \"character\" where id = ?", UUID.class, UUID.fromString(id)));
    }

    @Test
    void an_npc_cannot_go_to_a_reserve_nor_be_activated() {
        String id = npc("Innkeeper");

        Assertions.assertEquals(409, status(master, HttpMethod.DELETE, "/game-characters/" + id + "/adventure", null));
        Assertions.assertEquals(409, status(master, HttpMethod.PUT, "/game-characters/" + id + "/adventure",
                Map.of("adventureId", adventureId.toString())));
        Assertions.assertEquals(409, status(master, HttpMethod.PUT, "/adventures/" + adventureId + "/players/me/active-character", Map.of("characterId", id)));
        Assertions.assertEquals(adventureId, jdbc.queryForObject("select adventure_id from \"character\" where id = ?", UUID.class, UUID.fromString(id)));
    }

    @Test
    void my_characters_do_not_list_npcs() {
        String id = npc("Innkeeper");

        Assertions.assertEquals(0, body(call(HttpMethod.GET, "/game-characters/mine", master.token(), null)).size());
        Assertions.assertEquals(0, body(call(HttpMethod.GET, "/game-characters?ownerId=" + master.id(), master.token(), null)).size());
        Assertions.assertEquals(1, listedInAdventure(master, id));
    }

    @Test
    void the_npcs_stay_with_the_adventure_when_a_player_leaves_and_go_with_it_when_it_is_deleted() {
        String id = npc("Innkeeper");
        String played = body(createCharacterWithoutPicture(player, adventureId, "Gandalf")).get("id").asString();

        Assertions.assertEquals(200, status(player, HttpMethod.DELETE, "/adventures/" + adventureId + "/players/me", null));
        Assertions.assertNull(jdbc.queryForObject("select adventure_id from \"character\" where id = ?", UUID.class, UUID.fromString(played)));

        Assertions.assertEquals(204, status(master, HttpMethod.DELETE, "/adventures/" + adventureId, Map.of("confirmation", "Quest")));
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from \"character\" where id = ?", Integer.class, UUID.fromString(id)));
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from \"character\" where id = ?", Integer.class, UUID.fromString(played)));
    }

    @Test
    void the_npcs_of_an_adventure_go_with_their_pictures_when_it_is_deleted() throws Exception {
        String id = body(createCharacter(master, adventureId, "Innkeeper")).get("id").asString();
        String path = jdbc.queryForObject("select picture_path from \"character\" where id = ?", String.class, UUID.fromString(id));

        Assertions.assertEquals(204, status(master, HttpMethod.DELETE, "/adventures/" + adventureId, Map.of("confirmation", "Quest")));

        org.mockito.Mockito.verify(minioSpi).deleteAfterCommit(path);
    }
}
