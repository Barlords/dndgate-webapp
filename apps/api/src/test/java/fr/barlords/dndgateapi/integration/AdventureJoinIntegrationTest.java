package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

class AdventureJoinIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount master;

    private TestAccount player;

    private TestAccount stranger;

    @BeforeEach
    void fixture() {
        master = registerAndLogin();
        player = registerAndLogin();
        stranger = registerAndLogin();
    }

    private int joinById(TestAccount account, UUID adventureId) {
        return call(HttpMethod.POST, "/adventures/" + adventureId + "/join", account.token(), null).getStatusCode().value();
    }

    private int joinByCode(TestAccount account, UUID adventureId) {
        String code = jdbc.queryForObject("select invitation_code from adventure where id = ?", String.class, adventureId);
        return call(HttpMethod.POST, "/adventures/invitations/accept", account.token(), Map.of("code", code))
                .getStatusCode().value();
    }

    private UUID createAdventure(int maxPlayer, String visibility) {
        ResponseEntity<String> response = call(HttpMethod.POST, "/adventures", master.token(), Map.of(
                "name", "Quest", "description", "A quest", "maxPlayer", maxPlayer,
                "visibility", visibility, "postalCode", "75001", "city", "Paris"));
        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
        return UUID.fromString(body(response).get("id").asString());
    }

    private JsonNode adventure(UUID id) {
        return body(call(HttpMethod.GET, "/adventures/" + id, master.token(), null));
    }

    // ---- private adventures ----

    @Test
    void a_private_adventure_cannot_be_joined_by_its_id_but_can_be_with_its_invitation_code() {
        UUID adventureId = createAdventure(4, "PRIVATE");

        Assertions.assertEquals(404, joinById(stranger, adventureId), "the adventure must stay invisible");
        Assertions.assertEquals(200, joinByCode(stranger, adventureId));
        Assertions.assertTrue(adventure(adventureId).get("players").toString().contains(stranger.id().toString()));
    }

    @Test
    void a_wrong_invitation_code_finds_nothing() {
        createAdventure(4, "PRIVATE");

        Assertions.assertEquals(404, call(HttpMethod.POST, "/adventures/invitations/accept", stranger.token(),
                Map.of("code", "not-a-real-code")).getStatusCode().value());
    }

    // ---- public adventures ----

    @Test
    void a_public_adventure_can_be_joined_by_its_id() {
        UUID adventureId = createAdventure(4, "PUBLIC");

        Assertions.assertEquals(200, joinById(player, adventureId));
        Assertions.assertEquals(200, joinById(stranger, adventureId));
        Assertions.assertEquals(2, adventure(adventureId).get("players").size());
    }

    @Test
    void nobody_joins_twice_and_the_owner_is_already_a_participant() {
        UUID adventureId = createAdventure(4, "PUBLIC");
        joinById(player, adventureId);

        Assertions.assertEquals(409, joinById(player, adventureId));
        Assertions.assertEquals(409, joinById(master, adventureId));
        Assertions.assertEquals(1, adventure(adventureId).get("players").size());
    }

    // ---- capacity and status ----

    @Test
    void the_last_seat_starts_the_adventure_and_closes_it() {
        UUID adventureId = createAdventure(1, "PUBLIC");

        Assertions.assertEquals(200, joinById(player, adventureId));

        JsonNode joined = adventure(adventureId);
        Assertions.assertEquals("ONGOING", joined.get("status").asString());
        Assertions.assertEquals("PRIVATE", joined.get("visibility").asString());

        // nobody else gets in, neither by id (now invisible) nor with the invitation code
        Assertions.assertEquals(404, joinById(stranger, adventureId));
        Assertions.assertEquals(409, joinByCode(stranger, adventureId));
        Assertions.assertEquals(1, adventure(adventureId).get("players").size());
    }

    @Test
    void a_full_adventure_refuses_new_players_even_when_it_is_open_again() {
        UUID adventureId = createAdventure(2, "PUBLIC");
        joinById(player, adventureId);
        joinById(stranger, adventureId);
        // the second player took the last seat, which started the adventure; the API no longer lets an adventure
        // go back to NOT_STARTED, so the status is put back in the database to keep the capacity check under test
        jdbc.update("update adventure set status = 'NOT_STARTED' where id = ?", adventureId);
        TestAccount third = registerAndLogin();

        // 2 seats, 2 players: the status alone would allow it, the capacity must refuse it
        Assertions.assertEquals(409, joinByCode(third, adventureId));
    }

    private void setStatus(UUID adventureId, String status) {
        Assertions.assertEquals(200, call(HttpMethod.PUT, "/adventures/" + adventureId + "/status", master.token(),
                Map.of("status", status)).getStatusCode().value());
    }

    @Test
    void an_adventure_that_has_started_or_ended_refuses_new_players() {
        for (String status : new String[]{"ONGOING", "FINISH", "ABORT"}) {
            UUID adventureId = createAdventure(4, "PUBLIC");
            if (status.equals("FINISH")) {
                // FINISH is only reachable from ONGOING
                setStatus(adventureId, "ONGOING");
            }
            setStatus(adventureId, status);

            Assertions.assertEquals(409, joinById(player, adventureId), "by id, " + status);
            Assertions.assertEquals(409, joinByCode(player, adventureId), "by code, " + status);
        }
    }

    // ---- quitting ----

    @Test
    void a_player_can_quit_and_free_a_seat() {
        UUID adventureId = createAdventure(4, "PUBLIC");
        joinById(player, adventureId);
        joinById(stranger, adventureId);

        Assertions.assertEquals(200, call(HttpMethod.DELETE, "/adventures/" + adventureId + "/players/me", player.token(), null)
                .getStatusCode().value());

        String players = adventure(adventureId).get("players").toString();
        Assertions.assertFalse(players.contains(player.id().toString()));
        Assertions.assertTrue(players.contains(stranger.id().toString()));
    }

    @Test
    void someone_who_never_joined_cannot_quit() {
        UUID adventureId = createAdventure(4, "PUBLIC");

        Assertions.assertEquals(404, call(HttpMethod.DELETE, "/adventures/" + adventureId + "/players/me", stranger.token(), null)
                .getStatusCode().value());
    }
}
