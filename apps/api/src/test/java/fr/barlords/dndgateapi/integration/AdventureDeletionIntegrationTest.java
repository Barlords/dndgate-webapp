package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * The children of an adventure point to it through foreign keys without ON DELETE (review v2, point 44): the
 * deletion has to remove every kind of child itself, or the database refuses it.
 */
class AdventureDeletionIntegrationTest extends ApiIntegrationTestSupport {

    private int count(String table, UUID adventureId) {
        return jdbc.queryForObject("select count(*) from " + table + " where adventure_id = ?", Integer.class, adventureId);
    }

    @Test
    void an_adventure_is_deleted_with_every_kind_of_child() {
        TestAccount master = registerAndLogin();
        TestAccount player = registerAndLogin();
        TestAccount voter = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        for (TestAccount account : new TestAccount[]{player, voter}) {
            Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", account.token(), null)
                    .getStatusCode().value());
        }
        Assertions.assertEquals(201, createCharacter(player, adventureId, "Gandalf").getStatusCode().value());
        Assertions.assertEquals(201, call(HttpMethod.POST, "/adventures/" + adventureId + "/master/invitation", master.token(), null)
                .getStatusCode().value());

        UUID claimId = UUID.randomUUID();
        jdbc.update("insert into master_claims (id, adventure_id, candidate_id, created_at, expires_at, status) "
                + "values (?, ?, ?, now(), now() + interval '1 day', 'OPEN')", claimId, adventureId, player.id());
        jdbc.update("insert into master_claim_votes (claim_id, voter_id, approve) values (?, ?, true)", claimId, voter.id());
        jdbc.update("insert into message (id, adventure_id, sender_id, content, send_date) values (?, ?, ?, 'hello', now())",
                UUID.randomUUID(), adventureId, player.id());
        UUID planningId = UUID.randomUUID();
        jdbc.update("insert into planning (id, adventure_id) values (?, ?)", planningId, adventureId);
        jdbc.update("insert into propositions (planning_id, proposition_dates) values (?, '2027-01-01')", planningId);
        UUID availabilityId = UUID.randomUUID();
        jdbc.update("insert into availability (id, planning_id, account_id) values (?, ?, ?)", availabilityId, planningId, player.id());
        jdbc.update("insert into availability_dates (availability_id, availability_dates) values (?, '2027-01-01')", availabilityId);
        jdbc.update("insert into sent_invitations (id, sender_id, recipient_id, adventure_id, sent_at) values (?, ?, ?, ?, now())",
                UUID.randomUUID(), master.id(), player.id(), adventureId);
        Assertions.assertEquals(1, count("master_invitations", adventureId));

        ResponseEntity<String> deleted = call(HttpMethod.DELETE, "/adventures/" + adventureId, master.token(),
                java.util.Map.of("confirmation", "Quest"));

        Assertions.assertEquals(204, deleted.getStatusCode().value(), deleted.getBody());
        for (String table : new String[]{"adventure_players", "message", "planning", "master_claims", "master_invitations",
                "sent_invitations", "\"character\""}) {
            Assertions.assertEquals(0, count(table, adventureId), table);
        }
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from adventure where id = ?", Integer.class, adventureId));
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from master_claim_votes where claim_id = ?", Integer.class, claimId));
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from availability where id = ?", Integer.class, availabilityId));
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from availability_dates where availability_id = ?", Integer.class, availabilityId));
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from propositions where planning_id = ?", Integer.class, planningId));
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from \"character\" where account_id = ? and adventure_id is null",
                Integer.class, player.id()), "the character of the player survives in their reserve");
    }

    @Test
    void deleting_an_adventure_moves_player_characters_to_their_reserve_even_when_full() {
        TestAccount master = registerAndLogin();
        TestAccount player = registerAndLogin();
        subscribe(player, "test-tight");
        UUID adventureId = createAdventure(master, "PUBLIC");
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null)
                .getStatusCode().value());
        String waiting = body(createCharacterWithoutPicture(player, null, "Waiting")).get("id").asString();
        String played = body(createCharacter(player, adventureId, "Gandalf")).get("id").asString();
        String picture = jdbc.queryForObject("select picture_path from \"character\" where id = ?", String.class, UUID.fromString(played));
        Assertions.assertNotNull(picture);

        ResponseEntity<String> deleted = call(HttpMethod.DELETE, "/adventures/" + adventureId, master.token(),
                java.util.Map.of("confirmation", "Quest"));

        Assertions.assertEquals(204, deleted.getStatusCode().value(), deleted.getBody());
        Assertions.assertEquals(2, jdbc.queryForObject("select count(*) from \"character\" where account_id = ? and adventure_id is null",
                Integer.class, player.id()), "the reserve exceeds its quota, nothing is lost");
        Assertions.assertEquals(picture, jdbc.queryForObject("select picture_path from \"character\" where id = ?", String.class,
                UUID.fromString(played)));
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from \"character\" where id in (?, ?) and active",
                Integer.class, UUID.fromString(waiting), UUID.fromString(played)));
    }
}
