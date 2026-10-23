package fr.barlords.dndgateapi.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.barlords.dndgateapi.client.WebRTCMessage;
import fr.barlords.dndgateapi.client.dto.adventurenote.AdventureNotePatchRequest;
import fr.barlords.dndgateapi.client.dto.historicalnote.HistoricalNotePatchRequest;
import fr.barlords.dndgateapi.client.dto.message.MessageCreationRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The contract only describes what the API reads and does: no ignored fields, one route per operation, partial
 * PATCH bodies, an average that tells "no rating" apart from a zero.
 */
class ContractCleanupIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount master;

    private TestAccount player;

    private UUID adventureId;

    @BeforeEach
    void fixture() {
        master = registerAndLogin();
        player = registerAndLogin();
        adventureId = createAdventure(master, "PUBLIC");
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null)
                .getStatusCode().value());
    }

    private int status(TestAccount account, HttpMethod method, String path, Object body) {
        return call(method, path, account.token(), body).getStatusCode().value();
    }

    // ---- ignored fields ----

    @Test
    void the_contract_no_longer_declares_fields_the_api_ignores() throws Exception {
        Assertions.assertFalse(components(AdventureNotePatchRequest.class).contains("adventureId"));
        Assertions.assertFalse(components(HistoricalNotePatchRequest.class).contains("adventureId"));
        // accountId stays on the broadcast message (the server sets the sender) but is never read from a request
        JsonProperty property = WebRTCMessage.class.getDeclaredField("accountId").getAnnotation(JsonProperty.class);
        Assertions.assertNotNull(property);
        Assertions.assertEquals(JsonProperty.Access.READ_ONLY, property.access());
    }

    @Test
    void a_message_adventure_id_is_a_uuid_like_everywhere_else() throws Exception {
        Assertions.assertEquals(UUID.class, MessageCreationRequest.class.getRecordComponents()[0].getType());
        Assertions.assertEquals(400, status(player, HttpMethod.POST, "/messages", Map.of("adventureId", "not-a-uuid", "content", "hi")));
    }

    private static List<String> components(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    // ---- ratings ----

    @Test
    void a_rating_is_written_through_post_or_put_only() {
        Assertions.assertEquals(200, status(master, HttpMethod.PUT, "/adventures/" + adventureId + "/status", Map.of("status", "ONGOING")));
        ResponseEntity<String> created = call(HttpMethod.POST, "/ratings", player.token(),
                Map.of("accountTargetedId", master.id().toString(), "rate", 3));
        String ratingId = body(created).get("id").asString();

        Assertions.assertEquals(404, status(player, HttpMethod.PATCH, "/ratings/" + ratingId, Map.of("rate", 5)));
    }

    @Test
    void the_mean_tells_no_rating_apart_from_a_zero_average() {
        Assertions.assertEquals(200, status(master, HttpMethod.PUT, "/adventures/" + adventureId + "/status", Map.of("status", "ONGOING")));
        String path = "/ratings/mean?accountId=" + master.id();

        var none = body(call(HttpMethod.GET, path, player.token(), null));
        Assertions.assertTrue(none.get("average").isNull());
        Assertions.assertEquals(0, none.get("count").asInt());

        Assertions.assertEquals(201, status(player, HttpMethod.PUT, "/ratings", Map.of("accountTargetedId", master.id().toString(), "rate", 4)));
        var one = body(call(HttpMethod.GET, path, player.token(), null));
        Assertions.assertEquals(4.0, one.get("average").asDouble());
        Assertions.assertEquals(1, one.get("count").asInt());
    }

    // ---- PATCH is partial ----

    @Test
    void a_spellbook_patch_may_change_the_name_only() {
        String bookId = body(call(HttpMethod.POST, "/spellbooks", master.token(),
                Map.of("name", "Book", "description", "Spells", "ruleset", "dnd5e-2014", "spellList", List.of()))).get("id").asString();

        Assertions.assertEquals(200, status(master, HttpMethod.PATCH, "/spellbooks/" + bookId, Map.of("name", "Renamed")));
        var book = body(call(HttpMethod.GET, "/spellbooks/" + bookId, master.token(), null));
        Assertions.assertEquals("Renamed", book.get("name").asString());
        Assertions.assertEquals("Spells", book.get("description").asString());

        Assertions.assertEquals(200, status(master, HttpMethod.PATCH, "/spellbooks/" + bookId, Map.of("description", "New")));
        Assertions.assertEquals("Renamed", body(call(HttpMethod.GET, "/spellbooks/" + bookId, master.token(), null)).get("name").asString());
        Assertions.assertEquals(400, status(master, HttpMethod.PATCH, "/spellbooks/" + bookId, Map.of("name", "  ")));
    }

    @Test
    void a_character_keeps_the_same_minimum_length_at_creation_and_modification() {
        UUID characterId = UUID.fromString(body(createCharacter(master, adventureId, "Gandalf")).get("id").asString());
        String path = "/game-characters/" + characterId;

        Assertions.assertEquals(400, status(master, HttpMethod.PATCH, path, Map.of("name", "ab")));
        Assertions.assertEquals(400, status(master, HttpMethod.PATCH, path, Map.of("race", "ab")));
        Assertions.assertEquals(200, status(master, HttpMethod.PATCH, path, Map.of("name", "abc", "race", "abc")));
    }
}
