package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The features come from the plans: the picture of a character, the video of a table, and what an adventure reports. */
class FeatureIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount accountOn(String planId) {
        TestAccount account = registerAndLogin();
        subscribe(account, planId);
        return account;
    }

    private void assertFeatureProblem(ResponseEntity<String> response, String feature, String planId, String upgradePlanId) {
        Assertions.assertEquals(403, response.getStatusCode().value(), response.getBody());
        Assertions.assertTrue(response.getHeaders().getContentType().toString().contains("problem+json"));
        JsonNode problem = body(response);
        Assertions.assertEquals("urn:dndgate:feature-not-included", problem.get("type").asString());
        Assertions.assertEquals(feature, problem.get("feature").asString());
        Assertions.assertEquals(planId, problem.get("planId").asString());
        Assertions.assertEquals(upgradePlanId, problem.get("upgradePlanId").asString());
    }

    private ResponseEntity<String> offer(TestAccount account, UUID adventureId) {
        return call(HttpMethod.POST, "/webrtc/offer", account.token(),
                Map.of("type", "offer", "content", "sdp", "adventureId", adventureId.toString()));
    }

    @Test
    void a_tight_account_creates_a_character_without_picture() {
        TestAccount tight = accountOn("test-tight");

        ResponseEntity<String> created = createCharacterWithoutPicture(tight, null, "Faceless");

        Assertions.assertEquals(201, created.getStatusCode().value(), created.getBody());
        JsonNode character = body(created);
        Assertions.assertTrue(character.get("pictureUrl") == null || character.get("pictureUrl").isNull());
        Assertions.assertEquals(404, call(HttpMethod.GET, "/game-characters/" + character.get("id").asString() + "/picture",
                tight.token(), null).getStatusCode().value());
    }

    @Test
    void a_tight_account_sending_a_picture_gets_the_feature_problem() {
        TestAccount tight = accountOn("test-tight");

        assertFeatureProblem(createCharacter(tight, null, "Pictured"), "characterPictures", "test-tight", "test-paid");

        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from \"character\" where account_id = ?", Integer.class, tight.id()));
    }

    @Test
    void a_tight_player_sends_a_picture_in_the_equipped_table_of_a_paid_master() {
        TestAccount master = accountOn("test-paid");
        UUID adventureId = createAdventure(master, "PUBLIC");
        TestAccount player = accountOn("test-tight");
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null)
                .getStatusCode().value());

        Assertions.assertEquals(201, createCharacter(player, adventureId, "Shared").getStatusCode().value());
    }

    @Test
    void video_signals_are_refused_in_the_adventure_of_a_tight_master() {
        TestAccount tightMaster = accountOn("test-tight");
        UUID tightAdventure = createAdventure(tightMaster, "PUBLIC");
        TestAccount paidMaster = accountOn("test-paid");
        UUID paidAdventure = createAdventure(paidMaster, "PUBLIC");

        // The master's plan is the one reported: the caller cannot unlock the video by upgrading
        assertFeatureProblem(offer(tightMaster, tightAdventure), "video", "test-tight", "test-paid");
        TestAccount paidPlayer = accountOn("test-paid");
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + tightAdventure + "/join", paidPlayer.token(), null)
                .getStatusCode().value());
        assertFeatureProblem(offer(paidPlayer, tightAdventure), "video", "test-tight", "test-paid");

        Assertions.assertEquals(204, offer(paidMaster, paidAdventure).getStatusCode().value());
    }

    @Test
    void the_entitlements_of_an_adventure_list_features_and_usage() {
        TestAccount master = accountOn("test-tight");
        UUID adventureId = createAdventure(master, "PUBLIC");
        Assertions.assertEquals(201, createCharacterWithoutPicture(master, adventureId, "Hero").getStatusCode().value());
        Assertions.assertEquals(201, call(HttpMethod.POST, "/messages", master.token(),
                Map.of("adventureId", adventureId.toString(), "content", "hello")).getStatusCode().value());
        Assertions.assertEquals(201, call(HttpMethod.POST, "/adventure-notes", master.token(), Map.of(
                "adventureId", adventureId.toString(), "content", "n", "date", "2026-01-01", "visibility", "PUBLIC")).getStatusCode().value());

        ResponseEntity<String> tight = call(HttpMethod.GET, "/adventures/" + adventureId + "/entitlements", master.token(), null);

        Assertions.assertEquals(200, tight.getStatusCode().value(), tight.getBody());
        JsonNode entitlements = body(tight);
        Assertions.assertEquals(0, entitlements.get("features").size());
        JsonNode limits = entitlements.get("limits");
        Assertions.assertEquals(Set.of("charactersPerPlayerAndAdventure", "npcsPerAdventure", "messagesPerAdventure",
                "masterNotesPerAdventure", "historicalNotesPerAdventure"), keys(limits));
        assertUsage(limits, "charactersPerPlayerAndAdventure", 0, 1);
        // What the master creates in their own adventure is a non-player character, not a character they play
        assertUsage(limits, "npcsPerAdventure", 1, 1);
        assertUsage(limits, "messagesPerAdventure", 1, 1);
        assertUsage(limits, "masterNotesPerAdventure", 1, 1);
        assertUsage(limits, "historicalNotesPerAdventure", 0, 1);
        Assertions.assertNull(entitlements.get("planId"), "no plan id is given");

        TestAccount paidPlayer = accountOn("test-paid");
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", paidPlayer.token(), null)
                .getStatusCode().value());
        JsonNode seenByPlayer = body(call(HttpMethod.GET, "/adventures/" + adventureId + "/entitlements", paidPlayer.token(), null));
        assertUsage(seenByPlayer.get("limits"), "messagesPerAdventure", 1, 1);
        assertUsage(seenByPlayer.get("limits"), "charactersPerPlayerAndAdventure", 0, 1);
        Assertions.assertEquals(1, seenByPlayer.get("features").size(), "the pictures of the player plan, but no video of the master");
        Assertions.assertEquals("characterPictures", seenByPlayer.get("features").get(0).asString());

        subscribe(master, "test-paid");

        JsonNode paid = body(call(HttpMethod.GET, "/adventures/" + adventureId + "/entitlements", master.token(), null));
        Set<String> features = new HashSet<>();
        paid.get("features").forEach(feature -> features.add(feature.asString()));
        Assertions.assertEquals(Set.of("characterPictures", "video"), features);
        assertUsage(paid.get("limits"), "messagesPerAdventure", 1, 1000);
    }

    @Test
    void a_stranger_cannot_read_the_entitlements() {
        UUID adventureId = createAdventure(registerAndLogin(), "PUBLIC");

        Assertions.assertEquals(403, call(HttpMethod.GET, "/adventures/" + adventureId + "/entitlements",
                registerAndLogin().token(), null).getStatusCode().value());
    }

    private static Set<String> keys(JsonNode node) {
        return new HashSet<>(node.propertyNames());
    }

    private static void assertUsage(JsonNode limits, String key, long used, int max) {
        Assertions.assertEquals(used, limits.get(key).get("used").asLong(), key);
        Assertions.assertEquals(max, limits.get(key).get("max").asInt(), key);
    }
}
