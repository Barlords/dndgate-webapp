package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The magic items of an account, from creation to deletion: personal, like the spell books. */
class MagicItemIntegrationTest extends ApiIntegrationTestSupport {

    private static Map<String, Object> item(String name) {
        return Map.of("name", name, "type", "RING", "rarity", "RARE", "requiresAttunement", true,
                "description", "+1 to AC and saving throws");
    }

    private String create(TestAccount owner, String name) {
        ResponseEntity<String> created = call(HttpMethod.POST, "/magic-items", owner.token(), item(name));
        Assertions.assertEquals(201, created.getStatusCode().value(), created.getBody());
        return body(created).get("id").asString();
    }

    @Test
    void an_item_is_created_read_listed_by_name_changed_and_deleted_by_its_owner() {
        TestAccount owner = registerAndLogin();

        ResponseEntity<String> created = call(HttpMethod.POST, "/magic-items", owner.token(), item("  Ring of Protection  "));
        Assertions.assertEquals(201, created.getStatusCode().value(), created.getBody());
        JsonNode ring = body(created);
        Assertions.assertEquals("Ring of Protection", ring.get("name").asString());
        Assertions.assertEquals("RING", ring.get("type").asString());
        Assertions.assertEquals("RARE", ring.get("rarity").asString());
        Assertions.assertTrue(ring.get("requiresAttunement").asBoolean());
        String id = ring.get("id").asString();
        create(owner, "Amulet of Health");

        JsonNode mine = body(call(HttpMethod.GET, "/magic-items/mine", owner.token(), null));
        Assertions.assertEquals(2, mine.size());
        Assertions.assertEquals("Amulet of Health", mine.get(0).get("name").asString());
        Assertions.assertEquals("Ring of Protection", mine.get(1).get("name").asString());

        ResponseEntity<String> patched = call(HttpMethod.PATCH, "/magic-items/" + id, owner.token(),
                Map.of("rarity", "VERY_RARE", "requiresAttunement", false));
        Assertions.assertEquals(200, patched.getStatusCode().value(), patched.getBody());
        Assertions.assertEquals("VERY_RARE", body(patched).get("rarity").asString());
        Assertions.assertFalse(body(patched).get("requiresAttunement").asBoolean());
        Assertions.assertEquals("Ring of Protection", body(patched).get("name").asString());

        Assertions.assertEquals(400, call(HttpMethod.DELETE, "/magic-items/" + id, owner.token(),
                Map.of("confirmation", "ring of protection")).getStatusCode().value());
        Assertions.assertEquals(204, call(HttpMethod.DELETE, "/magic-items/" + id, owner.token(),
                Map.of("confirmation", "Ring of Protection")).getStatusCode().value());
        Assertions.assertEquals(404, call(HttpMethod.GET, "/magic-items/" + id, owner.token(), null).getStatusCode().value());
    }

    @Test
    void nobody_else_reads_changes_or_deletes_an_item() {
        TestAccount owner = registerAndLogin();
        TestAccount other = registerAndLogin();
        String id = create(owner, "Wand of Wonder");

        Assertions.assertEquals(403, call(HttpMethod.GET, "/magic-items/" + id, other.token(), null).getStatusCode().value());
        Assertions.assertEquals(403, call(HttpMethod.PATCH, "/magic-items/" + id, other.token(), Map.of("name", "Mine"))
                .getStatusCode().value());
        Assertions.assertEquals(403, call(HttpMethod.DELETE, "/magic-items/" + id, other.token(),
                Map.of("confirmation", "Wand of Wonder")).getStatusCode().value());
        Assertions.assertEquals(0, body(call(HttpMethod.GET, "/magic-items/mine", other.token(), null)).size());
        Assertions.assertEquals(401, call(HttpMethod.GET, "/magic-items/mine", null, null).getStatusCode().value());
    }

    @Test
    void an_invalid_item_is_refused() {
        TestAccount owner = registerAndLogin();

        Map<String, Object> unknownType = new HashMap<>(item("Cloak"));
        unknownType.put("type", "CLOAK");
        Map<String, Object> blankName = new HashMap<>(item("Cloak"));
        blankName.put("name", "  ");
        Map<String, Object> noRarity = new HashMap<>(item("Cloak"));
        noRarity.remove("rarity");

        for (Map<String, Object> invalid : new Map[]{unknownType, blankName, noRarity}) {
            ResponseEntity<String> refused = call(HttpMethod.POST, "/magic-items", owner.token(), invalid);
            Assertions.assertEquals(400, refused.getStatusCode().value(), refused.getBody());
        }
        Assertions.assertEquals(400, call(HttpMethod.GET, "/magic-items/not-a-uuid", owner.token(), null).getStatusCode().value());
        Assertions.assertEquals(404, call(HttpMethod.GET, "/magic-items/" + UUID.randomUUID(), owner.token(), null)
                .getStatusCode().value());
    }

    @Test
    void the_items_go_with_the_account() {
        TestAccount owner = registerAndLogin();
        String id = create(owner, "Bag of Holding");

        Assertions.assertEquals(204, deleteAccount(owner));

        Assertions.assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM magic_item WHERE id = ?", Long.class, UUID.fromString(id)));
    }
}
