package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

/**
 * A character can be kept outside any adventure of the application (one played at a real table): only its owner
 * reads it, and it goes with the account.
 */
class GameCharacterWithoutAdventureIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void a_character_created_without_adventure_belongs_to_its_owner_and_is_listed_among_theirs() {
        TestAccount owner = registerAndLogin();

        ResponseEntity<String> created = createCharacter(owner, null, "Aragorn");

        Assertions.assertEquals(201, created.getStatusCode().value(), created.getBody());
        JsonNode character = body(created);
        Assertions.assertTrue(character.get("adventureId").isNull());
        Assertions.assertEquals(owner.id().toString(), character.get("ownerId").asString());

        JsonNode mine = body(call(HttpMethod.GET, "/game-characters/mine", owner.token(), null));
        Assertions.assertEquals(1, mine.size());
        Assertions.assertEquals("Aragorn", mine.get(0).get("name").asString());
        Assertions.assertEquals(200, call(HttpMethod.GET, "/game-characters/" + character.get("id").asString(), owner.token(), null)
                .getStatusCode().value());
    }

    @Test
    void a_character_of_an_adventure_names_it() {
        TestAccount owner = registerAndLogin();
        UUID adventureId = createAdventure(owner, "PUBLIC");

        JsonNode character = body(createCharacter(owner, adventureId, "Legolas"));

        Assertions.assertEquals(adventureId.toString(), character.get("adventureId").asString());
    }

    @Test
    void nobody_else_reads_a_character_outside_any_adventure_nor_its_picture() {
        TestAccount owner = registerAndLogin();
        TestAccount other = registerAndLogin();
        String id = body(createCharacter(owner, null, "Boromir")).get("id").asString();

        Assertions.assertEquals(403, call(HttpMethod.GET, "/game-characters/" + id, other.token(), null).getStatusCode().value());
        Assertions.assertEquals(403, call(HttpMethod.GET, "/game-characters/" + id + "/picture", other.token(), null)
                .getStatusCode().value());
    }

    @Test
    void the_owner_changes_and_deletes_a_character_outside_any_adventure() {
        TestAccount owner = registerAndLogin();
        String id = body(createCharacter(owner, null, "Gimli")).get("id").asString();

        ResponseEntity<String> patched = call(HttpMethod.PATCH, "/game-characters/" + id, owner.token(), Map.of("race", "Dwarf"));
        Assertions.assertEquals(200, patched.getStatusCode().value(), patched.getBody());
        Assertions.assertEquals("Dwarf", body(patched).get("race").asString());

        Assertions.assertEquals(204, call(HttpMethod.DELETE, "/game-characters/" + id, owner.token(),
                Map.of("confirmation", "Gimli")).getStatusCode().value());
        Assertions.assertEquals(404, call(HttpMethod.GET, "/game-characters/" + id, owner.token(), null).getStatusCode().value());
    }

    @Test
    void the_characters_outside_any_adventure_go_with_the_account_and_those_of_an_adventure_stay() {
        TestAccount owner = registerAndLogin();
        UUID adventureId = createAdventure(registerAndLogin(), "PUBLIC");
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", owner.token(), null)
                .getStatusCode().value());
        String alone = body(createCharacter(owner, null, "Frodo")).get("id").asString();
        String played = body(createCharacter(owner, adventureId, "Samwise")).get("id").asString();

        Assertions.assertEquals(204, deleteAccount(owner));

        Assertions.assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"character\" WHERE id = ?", Long.class, UUID.fromString(alone)));
        Assertions.assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"character\" WHERE id = ?", Long.class, UUID.fromString(played)));
    }
}
