package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** The reserve of characters outside any adventure, the character a player plays, and moving a character in and out. */
class CharacterReserveIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount playerOn(UUID adventureId, String planId) {
        TestAccount player = registerAndLogin();
        subscribe(player, planId);
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null)
                .getStatusCode().value());
        return player;
    }

    private TestAccount masterOn(String planId) {
        TestAccount master = registerAndLogin();
        subscribe(master, planId);
        return master;
    }

    private String create(TestAccount account, UUID adventureId, String name) {
        ResponseEntity<String> created = createCharacterWithoutPicture(account, adventureId, name);
        Assertions.assertEquals(201, created.getStatusCode().value(), created.getBody());
        return body(created).get("id").asString();
    }

    private JsonNode move(TestAccount account, String characterId, UUID adventureId) {
        ResponseEntity<String> response = call(HttpMethod.PUT, "/game-characters/" + characterId + "/adventure", account.token(),
                Map.of("adventureId", adventureId.toString()));
        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
        return body(response);
    }

    private JsonNode toReserve(TestAccount account, String characterId) {
        ResponseEntity<String> response = call(HttpMethod.DELETE, "/game-characters/" + characterId + "/adventure", account.token(), null);
        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
        return body(response);
    }

    private ResponseEntity<String> activate(TestAccount account, UUID adventureId, String characterId) {
        return call(HttpMethod.PUT, "/adventures/" + adventureId + "/players/me/active-character", account.token(),
                Map.of("characterId", characterId));
    }

    private boolean isActive(TestAccount account, String characterId) {
        return body(call(HttpMethod.GET, "/game-characters/" + characterId, account.token(), null)).get("active").asBoolean();
    }

    @Test
    void a_player_moves_a_character_out_and_back_in() {
        TestAccount master = masterOn("test-paid");
        UUID adventureId = createAdventure(master, "PUBLIC");
        TestAccount player = playerOn(adventureId, "test-default");
        String id = create(player, adventureId, "Gandalf");
        Assertions.assertTrue(isActive(player, id), "the first character of a player in an adventure is the one they play");

        JsonNode inReserve = toReserve(player, id);

        Assertions.assertTrue(inReserve.get("adventureId").isNull());
        Assertions.assertFalse(inReserve.get("active").asBoolean());
        Assertions.assertEquals(0, body(call(HttpMethod.GET, "/game-characters?adventureId=" + adventureId, player.token(), null)).size());

        JsonNode back = move(player, id, adventureId);

        Assertions.assertEquals(adventureId.toString(), back.get("adventureId").asString());
        Assertions.assertTrue(back.get("active").asBoolean());
    }

    @Test
    void a_second_character_does_not_take_the_active_place_until_it_is_activated() {
        TestAccount master = masterOn("test-paid");
        UUID adventureId = createAdventure(master, "PUBLIC");
        TestAccount player = playerOn(adventureId, "test-default");
        String first = create(player, adventureId, "First");
        String second = create(player, adventureId, "Second");

        Assertions.assertTrue(isActive(player, first));
        Assertions.assertFalse(isActive(player, second));

        ResponseEntity<String> activated = activate(player, adventureId, second);

        Assertions.assertEquals(200, activated.getStatusCode().value(), activated.getBody());
        Assertions.assertTrue(body(activated).get("active").asBoolean());
        Assertions.assertTrue(isActive(player, second));
        Assertions.assertFalse(isActive(player, first));
    }

    @Test
    void a_full_reserve_answers_the_plan_problem_with_the_reserve_limit() {
        TestAccount master = masterOn("test-paid");
        UUID adventureId = createAdventure(master, "PUBLIC");
        TestAccount player = playerOn(adventureId, "test-tight");
        create(player, null, "In the reserve");
        String played = create(player, adventureId, "In the adventure");

        ResponseEntity<String> refused = call(HttpMethod.DELETE, "/game-characters/" + played + "/adventure", player.token(), null);

        Assertions.assertEquals(409, refused.getStatusCode().value(), refused.getBody());
        JsonNode problem = body(refused);
        Assertions.assertEquals("urn:dndgate:plan-limit-reached", problem.get("type").asString());
        Assertions.assertEquals("charactersWithoutAdventure", problem.get("limit").asString());
        Assertions.assertEquals(adventureId.toString(),
                body(call(HttpMethod.GET, "/game-characters/" + played, player.token(), null)).get("adventureId").asString());
    }

    @Test
    void moving_into_an_adventure_follows_the_plan_of_its_master() {
        TestAccount master = masterOn("test-tight");
        UUID adventureId = createAdventure(master, "PUBLIC");
        TestAccount player = playerOn(adventureId, "test-paid");
        create(player, adventureId, "Already there");
        String waiting = create(player, null, "Waiting");

        ResponseEntity<String> refused = call(HttpMethod.PUT, "/game-characters/" + waiting + "/adventure", player.token(),
                Map.of("adventureId", adventureId.toString()));

        Assertions.assertEquals(409, refused.getStatusCode().value(), refused.getBody());
        Assertions.assertEquals("charactersPerPlayerAndAdventure", body(refused).get("limit").asString());
    }

    @Test
    void the_per_adventure_limit_is_per_player() {
        TestAccount master = masterOn("test-tight");
        UUID adventureId = createAdventure(master, "PUBLIC");
        TestAccount first = playerOn(adventureId, "test-default");
        TestAccount second = playerOn(adventureId, "test-default");
        create(first, adventureId, "One");
        Assertions.assertEquals(409, createCharacterWithoutPicture(first, adventureId, "Two").getStatusCode().value());

        Assertions.assertEquals(201, createCharacterWithoutPicture(second, adventureId, "Other player").getStatusCode().value());
    }

    @Test
    void only_the_owner_moves_or_activates_and_only_a_player_moves_in() {
        TestAccount master = masterOn("test-paid");
        UUID adventureId = createAdventure(master, "PUBLIC");
        TestAccount player = playerOn(adventureId, "test-default");
        TestAccount stranger = registerAndLogin();
        String played = create(player, adventureId, "Played");
        String waiting = create(player, null, "Waiting");
        String ofMaster = create(master, null, "Of the master");

        Assertions.assertEquals(403, call(HttpMethod.DELETE, "/game-characters/" + played + "/adventure", stranger.token(), null)
                .getStatusCode().value());
        Assertions.assertEquals(403, call(HttpMethod.PUT, "/game-characters/" + waiting + "/adventure", stranger.token(),
                Map.of("adventureId", adventureId.toString())).getStatusCode().value());
        Assertions.assertEquals(403, activate(stranger, adventureId, played).getStatusCode().value());
        Assertions.assertEquals(403, call(HttpMethod.PUT, "/game-characters/" + ofMaster + "/adventure", master.token(),
                Map.of("adventureId", adventureId.toString())).getStatusCode().value(), "the master plays no character");
        Assertions.assertEquals(400, call(HttpMethod.PUT, "/game-characters/" + waiting + "/adventure", player.token(),
                Map.of()).getStatusCode().value());
    }

    @Test
    void a_character_in_an_adventure_is_neither_moved_in_again_nor_activated_elsewhere() {
        TestAccount master = masterOn("test-paid");
        UUID adventureId = createAdventure(master, "PUBLIC");
        UUID otherAdventureId = createAdventure(masterOn("test-paid"), "PUBLIC");
        TestAccount player = playerOn(adventureId, "test-default");
        String played = create(player, adventureId, "Played");
        String waiting = create(player, null, "Waiting");

        Assertions.assertEquals(409, call(HttpMethod.PUT, "/game-characters/" + played + "/adventure", player.token(),
                Map.of("adventureId", adventureId.toString())).getStatusCode().value());
        Assertions.assertEquals(409, activate(player, adventureId, waiting).getStatusCode().value());
        Assertions.assertEquals(409, activate(player, otherAdventureId, played).getStatusCode().value());
    }

    @Test
    void two_active_characters_are_impossible() throws Exception {
        TestAccount master = masterOn("test-paid");
        UUID adventureId = createAdventure(master, "PUBLIC");
        TestAccount player = playerOn(adventureId, "test-default");
        List<String> ids = List.of(create(player, adventureId, "Alpha"), create(player, adventureId, "Beta"), create(player, adventureId, "Gamma"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String id : ids.subList(1, 3)) {
                results.add(pool.submit(() -> {
                    start.await();
                    return activate(player, adventureId, id).getStatusCode().value();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        Assertions.assertTrue(statuses.contains(200), statuses.toString());
        Assertions.assertTrue(statuses.stream().allMatch(status -> status == 200 || status == 409), statuses.toString());
        Long active = jdbc.queryForObject("SELECT count(*) FROM \"character\" WHERE adventure_id = ? AND account_id = ? AND active",
                Long.class, adventureId, player.id());
        Assertions.assertEquals(1L, active);
    }

    @Test
    void the_unique_index_refuses_two_active_characters_even_if_the_service_is_bypassed() {
        TestAccount master = masterOn("test-paid");
        UUID adventureId = createAdventure(master, "PUBLIC");
        TestAccount player = playerOn(adventureId, "test-default");
        create(player, adventureId, "Alpha");
        String second = create(player, adventureId, "Beta");

        Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE \"character\" SET active = true WHERE id = ?", UUID.fromString(second)));
    }
}
