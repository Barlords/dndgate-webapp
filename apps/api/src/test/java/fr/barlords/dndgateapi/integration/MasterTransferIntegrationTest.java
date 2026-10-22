package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class MasterTransferIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount master;

    private TestAccount first;

    private TestAccount second;

    private TestAccount stranger;

    private UUID adventureId;

    private String adventurePath;

    @BeforeEach
    void fixture() {
        master = registerAndLogin();
        first = registerAndLogin();
        second = registerAndLogin();
        stranger = registerAndLogin();
        adventureId = createAdventure(master, "PUBLIC");
        adventurePath = "/adventures/" + adventureId;
        join(first, adventureId);
        join(second, adventureId);
        jdbc.update("update adventure set status = 'ONGOING' where id = ?", adventureId);
    }

    // ---------------------------------------------------------------- helpers

    private void join(TestAccount account, UUID adventure) {
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventure + "/join", account.token(), null).getStatusCode().value());
    }

    /** Players only join before the adventure starts. */
    private void joinWhileOpen(TestAccount account) {
        jdbc.update("update adventure set status = 'NOT_STARTED' where id = ?", adventureId);
        join(account, adventureId);
        jdbc.update("update adventure set status = 'ONGOING' where id = ?", adventureId);
    }

    private int status(TestAccount account, HttpMethod method, String path, Object body) {
        return call(method, path, account.token(), body).getStatusCode().value();
    }

    private JsonNode adventure() {
        return body(call(HttpMethod.GET, adventurePath, master.token(), null));
    }

    private UUID ownerId() {
        return jdbc.queryForObject("select creator_id from adventure where id = ?", UUID.class, adventureId);
    }

    private List<UUID> players() {
        return jdbc.queryForList("select player_id from adventure_players where adventure_id = ?", UUID.class, adventureId);
    }

    private void stepDown() {
        ResponseEntity<String> response = call(HttpMethod.POST, adventurePath + "/master/step-down", master.token(), Map.of("confirmation", "Quest"));
        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
    }

    private String invite() {
        ResponseEntity<String> response = call(HttpMethod.POST, adventurePath + "/master/invitation", master.token(), null);
        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
        return body(response).get("code").asString();
    }

    private int accept(TestAccount account, String code) {
        return status(account, HttpMethod.POST, "/adventures/master/accept", Map.of("code", code));
    }

    private ResponseEntity<String> claim(TestAccount account) {
        return call(HttpMethod.POST, adventurePath + "/master/claim", account.token(), null);
    }

    private ResponseEntity<String> vote(TestAccount account, boolean approve) {
        return call(HttpMethod.PUT, adventurePath + "/master/claim/vote", account.token(), Map.of("approve", approve));
    }

    private String claimStatus() {
        return jdbc.queryForObject("select status from master_claims where adventure_id = ? order by created_at desc limit 1", String.class, adventureId);
    }

    // ---------------------------------------------------------------- stepping down

    @Test
    void the_master_steps_down_after_retyping_the_name_and_loses_every_right() {
        Assertions.assertEquals(400, status(master, HttpMethod.POST, adventurePath + "/master/step-down", Map.of("confirmation", "quest")));
        Assertions.assertFalse(adventure().get("masterVacant").asBoolean());

        stepDown();

        Assertions.assertTrue(adventure().get("masterVacant").asBoolean());
        Assertions.assertEquals(master.id(), ownerId(), "the history keeps the former master");
        Assertions.assertEquals(403, status(master, HttpMethod.PATCH, adventurePath, Map.of("name", "Mine")));
        Assertions.assertEquals(403, status(master, HttpMethod.PUT, adventurePath + "/status", Map.of("status", "FINISH")));
        Assertions.assertEquals(403, status(master, HttpMethod.POST, adventurePath + "/master/invitation", null));
        Assertions.assertEquals(200, status(master, HttpMethod.GET, adventurePath, null), "they still read what they took part in");
    }

    @Test
    void nobody_has_the_rights_of_a_master_while_the_seat_is_vacant() {
        stepDown();

        for (TestAccount account : new TestAccount[]{master, first, second, stranger}) {
            Assertions.assertEquals(403, status(account, HttpMethod.PATCH, adventurePath, Map.of("name", "Taken")));
        }
        Assertions.assertEquals("Quest", jdbc.queryForObject("select name from adventure where id = ?", String.class, adventureId));
    }

    @Test
    void only_the_master_steps_down_and_only_once() {
        Assertions.assertEquals(403, status(first, HttpMethod.POST, adventurePath + "/master/step-down", Map.of("confirmation", "Quest")));
        stepDown();
        Assertions.assertEquals(403, status(master, HttpMethod.POST, adventurePath + "/master/step-down", Map.of("confirmation", "Quest")));
    }

    @Test
    void an_adventure_that_is_over_keeps_its_master() {
        jdbc.update("update adventure set status = 'FINISH' where id = ?", adventureId);

        Assertions.assertEquals(409, status(master, HttpMethod.POST, adventurePath + "/master/step-down", Map.of("confirmation", "Quest")));
        Assertions.assertEquals(409, status(master, HttpMethod.POST, adventurePath + "/master/invitation", null));
    }

    // ---------------------------------------------------------------- invitation

    @Test
    void the_holder_of_the_code_becomes_master_and_the_former_master_takes_a_seat() {
        String code = invite();

        Assertions.assertEquals(200, accept(stranger, code));

        Assertions.assertEquals(stranger.id(), ownerId());
        Assertions.assertTrue(players().contains(master.id()), "the former master is now a player");
        Assertions.assertFalse(adventure().get("masterVacant").asBoolean());
        Assertions.assertEquals(200, status(stranger, HttpMethod.PATCH, adventurePath, Map.of("name", "Renamed")));
        Assertions.assertEquals(403, status(master, HttpMethod.PATCH, adventurePath, Map.of("name", "Back")));
    }

    @Test
    void a_player_who_accepts_leaves_his_seat_to_the_former_master() {
        Assertions.assertEquals(200, accept(first, invite()));

        Assertions.assertEquals(first.id(), ownerId());
        Assertions.assertFalse(players().contains(first.id()));
        Assertions.assertTrue(players().contains(master.id()));
        Assertions.assertEquals(2, players().size());
    }

    @Test
    void when_every_seat_is_taken_the_former_master_leaves_but_keeps_the_characters() {
        jdbc.update("update adventure set max_player = 2 where id = ?", adventureId);
        Assertions.assertEquals(201, createCharacter(master, adventureId, "Gandalf").getStatusCode().value());

        Assertions.assertEquals(200, accept(stranger, invite()));

        Assertions.assertFalse(players().contains(master.id()));
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from \"character\" where adventure_id = ? and account_id = ?", Integer.class, adventureId, master.id()));
        Assertions.assertEquals(403, status(master, HttpMethod.GET, adventurePath + "/master/claim", null));
    }

    @Test
    void a_code_works_once() {
        String code = invite();

        Assertions.assertEquals(200, accept(stranger, code));
        Assertions.assertEquals(404, accept(registerAndLogin(), code));
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from master_invitations where adventure_id = ?", Integer.class, adventureId));
    }

    @Test
    void two_people_using_the_code_at_the_same_moment_are_not_both_masters() throws Exception {
        String code = invite();
        TestAccount other = registerAndLogin();

        List<Integer> statuses = inParallel(List.of(
                () -> accept(stranger, code),
                () -> accept(other, code)));

        Assertions.assertEquals(List.of(200, 404), statuses.stream().sorted().toList());
        Assertions.assertTrue(List.of(stranger.id(), other.id()).contains(ownerId()));
    }

    @Test
    void a_new_invitation_replaces_the_previous_one() {
        String old = invite();
        String current = invite();

        Assertions.assertEquals(404, accept(stranger, old));
        Assertions.assertEquals(200, accept(stranger, current));
    }

    @Test
    void a_revoked_invitation_does_not_work_and_only_the_master_revokes() {
        String code = invite();

        Assertions.assertEquals(403, status(first, HttpMethod.DELETE, adventurePath + "/master/invitation", null));
        Assertions.assertEquals(204, status(master, HttpMethod.DELETE, adventurePath + "/master/invitation", null));

        Assertions.assertEquals(404, accept(stranger, code));
    }

    @Test
    void an_expired_invitation_does_not_work() {
        String code = invite();
        jdbc.update("update master_invitations set expires_at = now() - interval '1 second' where adventure_id = ?", adventureId);

        Assertions.assertEquals(404, accept(stranger, code));
    }

    @Test
    void only_the_master_can_invite() {
        Assertions.assertEquals(403, status(first, HttpMethod.POST, adventurePath + "/master/invitation", null));
        Assertions.assertEquals(403, status(stranger, HttpMethod.POST, adventurePath + "/master/invitation", null));
    }

    @Test
    void the_master_cannot_use_their_own_code_and_it_stays_valid() {
        String code = invite();

        Assertions.assertEquals(409, accept(master, code));
        Assertions.assertEquals(200, accept(stranger, code));
    }

    @Test
    void the_code_is_not_stored_in_clear_and_stepping_down_cancels_it() {
        String code = invite();

        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from master_invitations where code_hash = ?", Integer.class, code));

        stepDown();

        Assertions.assertEquals(404, accept(stranger, code));
    }

    @Test
    void a_wrong_or_empty_code_is_refused() {
        Assertions.assertEquals(404, accept(stranger, "not-a-code"));
        Assertions.assertEquals(400, status(stranger, HttpMethod.POST, "/adventures/master/accept", Map.of("code", " ")));
        Assertions.assertEquals(401, call(HttpMethod.POST, "/adventures/master/accept", null, Map.of("code", "x")).getStatusCode().value());
    }

    // ---------------------------------------------------------------- the vote

    @Test
    void the_role_cannot_be_asked_while_there_is_a_master() {
        Assertions.assertEquals(409, claim(first).getStatusCode().value());
    }

    @Test
    void only_a_player_can_ask() {
        stepDown();

        Assertions.assertEquals(403, claim(stranger).getStatusCode().value());
        Assertions.assertEquals(403, claim(master).getStatusCode().value(), "the former master is not a player");
    }

    @Test
    void the_other_player_approves_and_the_role_changes_hands() {
        stepDown();

        ResponseEntity<String> asked = claim(first);
        Assertions.assertEquals(201, asked.getStatusCode().value(), asked.getBody());
        Assertions.assertEquals("OPEN", body(asked).get("status").asString());
        Assertions.assertEquals(1, body(asked).get("voters").size());
        Assertions.assertEquals(second.id().toString(), body(asked).get("voters").get(0).get("accountId").asString());
        Assertions.assertEquals(ownerId(), master.id(), "nothing changes before the vote is over");

        ResponseEntity<String> voted = vote(second, true);

        Assertions.assertEquals(200, voted.getStatusCode().value(), voted.getBody());
        Assertions.assertEquals("ACCEPTED", body(voted).get("status").asString());
        Assertions.assertEquals(first.id(), ownerId());
        Assertions.assertFalse(players().contains(first.id()));
        Assertions.assertTrue(players().contains(master.id()), "the former master takes the seat that was freed");
        Assertions.assertFalse(adventure().get("masterVacant").asBoolean());
        Assertions.assertEquals(200, status(first, HttpMethod.PATCH, adventurePath, Map.of("name", "Reclaimed")));
    }

    @Test
    void one_refusal_ends_the_vote_and_nothing_changes() {
        stepDown();
        claim(first);

        ResponseEntity<String> voted = vote(second, false);

        Assertions.assertEquals("REJECTED", body(voted).get("status").asString());
        Assertions.assertEquals(master.id(), ownerId());
        Assertions.assertTrue(adventure().get("masterVacant").asBoolean());
        Assertions.assertEquals(404, call(HttpMethod.GET, adventurePath + "/master/claim", second.token(), null).getStatusCode().value());
        Assertions.assertEquals(201, claim(first).getStatusCode().value(), "the candidate can ask again");
    }

    @Test
    void everybody_has_to_approve() {
        TestAccount third = registerAndLogin();
        joinWhileOpen(third);
        stepDown();
        claim(first);

        ResponseEntity<String> afterFirst = vote(second, true);
        Assertions.assertEquals("OPEN", body(afterFirst).get("status").asString());
        Assertions.assertEquals(master.id(), ownerId());

        ResponseEntity<String> afterSecond = vote(third, true);
        Assertions.assertEquals("ACCEPTED", body(afterSecond).get("status").asString());
        Assertions.assertEquals(first.id(), ownerId());
    }

    @Test
    void the_candidate_alone_with_a_vacant_seat_takes_the_role_at_once() {
        status(second, HttpMethod.DELETE, adventurePath + "/players/me", null);
        stepDown();

        ResponseEntity<String> asked = claim(first);

        Assertions.assertEquals("ACCEPTED", body(asked).get("status").asString());
        Assertions.assertEquals(first.id(), ownerId());
    }

    @Test
    void the_candidate_and_strangers_cannot_vote() {
        stepDown();
        claim(first);

        Assertions.assertEquals(403, vote(first, true).getStatusCode().value());
        Assertions.assertEquals(403, vote(stranger, true).getStatusCode().value());
        Assertions.assertEquals(403, vote(master, true).getStatusCode().value());
        Assertions.assertEquals(master.id(), ownerId());
    }

    @Test
    void one_vote_at_a_time_and_nobody_votes_without_one() {
        stepDown();
        Assertions.assertEquals(404, vote(second, true).getStatusCode().value());

        claim(first);
        Assertions.assertEquals(409, claim(second).getStatusCode().value());
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from master_claims where adventure_id = ?", Integer.class, adventureId));
    }

    @Test
    void the_vote_is_readable_by_the_participants_only() {
        stepDown();
        claim(first);

        for (TestAccount reader : new TestAccount[]{first, second, master}) {
            ResponseEntity<String> response = call(HttpMethod.GET, adventurePath + "/master/claim", reader.token(), null);
            Assertions.assertEquals(200, response.getStatusCode().value());
            Assertions.assertEquals(first.id().toString(), body(response).get("candidateId").asString());
        }
        Assertions.assertEquals(403, status(stranger, HttpMethod.GET, adventurePath + "/master/claim", null));
    }

    @Test
    void the_votes_are_visible_and_an_answer_can_still_change() {
        TestAccount third = registerAndLogin();
        joinWhileOpen(third);
        stepDown();
        claim(first);
        vote(second, true);

        JsonNode voters = body(call(HttpMethod.GET, adventurePath + "/master/claim", first.token(), null)).get("voters");
        Assertions.assertEquals(2, voters.size());
        for (JsonNode voter : voters) {
            Assertions.assertEquals(voter.get("accountId").asString().equals(second.id().toString()), voter.get("approved").asBoolean(false));
        }

        Assertions.assertEquals("REJECTED", body(vote(second, false)).get("status").asString());
    }

    @Test
    void the_candidate_can_give_up_and_nobody_else_can_cancel() {
        stepDown();
        claim(first);

        Assertions.assertEquals(403, status(second, HttpMethod.DELETE, adventurePath + "/master/claim", null));
        Assertions.assertEquals(204, status(first, HttpMethod.DELETE, adventurePath + "/master/claim", null));

        Assertions.assertEquals("CANCELLED", claimStatus());
        Assertions.assertEquals(201, claim(second).getStatusCode().value(), "somebody else can ask now");
    }

    @Test
    void a_vote_nobody_answers_ends_when_its_time_is_over() {
        stepDown();
        claim(first);
        jdbc.update("update master_claims set expires_at = now() - interval '1 second' where adventure_id = ?", adventureId);

        Assertions.assertEquals(404, vote(second, true).getStatusCode().value());
        Assertions.assertEquals(404, call(HttpMethod.GET, adventurePath + "/master/claim", first.token(), null).getStatusCode().value());
        Assertions.assertEquals(master.id(), ownerId());

        Assertions.assertEquals(201, claim(second).getStatusCode().value(), "a new vote can start");
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from master_claims where adventure_id = ? and status = 'EXPIRED'", Integer.class, adventureId));
    }

    @Test
    void a_vote_is_cancelled_when_the_candidate_leaves_the_adventure() {
        stepDown();
        claim(first);
        status(first, HttpMethod.DELETE, adventurePath + "/players/me", null);

        ResponseEntity<String> voted = vote(second, true);

        Assertions.assertEquals("CANCELLED", body(voted).get("status").asString());
        Assertions.assertEquals(master.id(), ownerId());
    }

    @Test
    void a_candidate_who_deletes_the_account_cancels_the_vote_and_another_player_can_ask_at_once() {
        joinWhileOpen(stranger);
        stepDown();
        claim(first);

        Assertions.assertEquals(204, deleteAccount(first));

        Assertions.assertEquals("CANCELLED", claimStatus());
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from master_claims where adventure_id = ? and status = 'OPEN'", Integer.class, adventureId));
        Assertions.assertEquals(201, claim(second).getStatusCode().value());
        Assertions.assertEquals("OPEN", claimStatus());
    }

    @Test
    void a_vote_left_open_for_an_anonymized_candidate_never_hands_over_the_role() {
        stepDown();
        claim(first);
        jdbc.update("update accounts set anonymized_at = now() where id = ?", first.id());

        ResponseEntity<String> voted = vote(second, true);

        Assertions.assertEquals("CANCELLED", body(voted).get("status").asString());
        Assertions.assertEquals("CANCELLED", claimStatus());
        Assertions.assertEquals(master.id(), ownerId());
        Assertions.assertNotNull(jdbc.queryForObject("select master_vacant_since from adventure where id = ?", Object.class, adventureId));
    }

    @Test
    void the_players_vote_at_the_same_moment_and_the_role_changes_hands_exactly_once() throws Exception {
        List<TestAccount> others = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            TestAccount account = registerAndLogin();
            jdbc.update("update adventure set max_player = 10 where id = ?", adventureId);
            joinWhileOpen(account);
            others.add(account);
        }
        stepDown();
        claim(first);
        List<TestAccount> voters = new ArrayList<>(others);
        voters.add(second);

        List<Integer> statuses = inParallel(voters.stream().<java.util.concurrent.Callable<Integer>>map(voter -> () -> vote(voter, true).getStatusCode().value()).toList());

        Assertions.assertTrue(statuses.stream().allMatch(s -> s == 200 || s == 404), statuses.toString());
        Assertions.assertEquals(first.id(), ownerId());
        Assertions.assertEquals("ACCEPTED", claimStatus());
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from master_claims where adventure_id = ? and status = 'OPEN'", Integer.class, adventureId));
        Assertions.assertTrue(players().contains(master.id()));
        Assertions.assertFalse(players().contains(first.id()));
        Assertions.assertEquals(players().size(), players().stream().distinct().count());
    }

    @Test
    void two_players_asking_at_the_same_moment_start_only_one_vote() throws Exception {
        stepDown();

        List<Integer> statuses = inParallel(List.of(
                () -> claim(first).getStatusCode().value(),
                () -> claim(second).getStatusCode().value()));

        Assertions.assertEquals(List.of(201, 409), statuses.stream().sorted().toList());
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from master_claims where adventure_id = ? and status = 'OPEN'", Integer.class, adventureId));
    }

    // ---------------------------------------------------------------- a deleted account

    @Test
    void when_the_master_deletes_the_account_the_players_can_take_over_the_adventure() {
        deleteAccount(master);

        Assertions.assertTrue(adventure(first).get("masterVacant").asBoolean());
        Assertions.assertEquals("ONGOING", jdbc.queryForObject("select status from adventure where id = ?", String.class, adventureId));

        Assertions.assertEquals(201, claim(first).getStatusCode().value());
        Assertions.assertEquals("ACCEPTED", body(vote(second, true)).get("status").asString());

        Assertions.assertEquals(first.id(), ownerId());
        Assertions.assertFalse(players().contains(master.id()), "the deleted account does not take a seat");
        Assertions.assertEquals(List.of(second.id()), players());
    }

    @Test
    void the_players_whose_account_was_deleted_are_not_asked_to_vote() {
        deleteAccount(second);
        stepDown();

        ResponseEntity<String> asked = claim(first);

        Assertions.assertEquals("ACCEPTED", body(asked).get("status").asString(), "nobody is left to ask");
        Assertions.assertEquals(first.id(), ownerId());
    }

    @Test
    void an_adventure_that_has_not_started_is_still_aborted_when_its_master_deletes_the_account() {
        jdbc.update("update adventure set status = 'NOT_STARTED' where id = ?", adventureId);

        deleteAccount(master);

        Assertions.assertEquals("ABORT", jdbc.queryForObject("select status from adventure where id = ?", String.class, adventureId));
        Assertions.assertEquals(409, claim(first).getStatusCode().value(), "an aborted adventure is not given to anybody");
    }

    private JsonNode adventure(TestAccount reader) {
        return body(call(HttpMethod.GET, adventurePath, reader.token(), null));
    }

    // ---------------------------------------------------------------- what goes with the role

    @Test
    void the_private_notes_follow_the_role() {
        UUID noteId = UUID.randomUUID();
        jdbc.update("insert into adventure_note (id, adventure_id, content, date, visibility) values (?, ?, ?, ?, 'PRIVATE')",
                noteId, adventureId, "the villain is the innkeeper", LocalDate.of(2026, 1, 1));
        Assertions.assertTrue(call(HttpMethod.GET, "/adventure-notes?adventureId=" + adventureId, master.token(), null).getBody().contains("innkeeper"));
        Assertions.assertFalse(call(HttpMethod.GET, "/adventure-notes?adventureId=" + adventureId, first.token(), null).getBody().contains("innkeeper"));

        Assertions.assertEquals(200, accept(first, invite()));

        Assertions.assertTrue(call(HttpMethod.GET, "/adventure-notes?adventureId=" + adventureId, first.token(), null).getBody().contains("innkeeper"));
        Assertions.assertFalse(call(HttpMethod.GET, "/adventure-notes?adventureId=" + adventureId, master.token(), null).getBody().contains("innkeeper"),
                "the former master is a player now");
        Assertions.assertEquals(403, status(master, HttpMethod.GET, "/adventure-notes/" + noteId, null));
    }

    @Test
    void while_the_seat_is_vacant_nobody_reads_the_private_notes() {
        jdbc.update("insert into adventure_note (id, adventure_id, content, date, visibility) values (?, ?, ?, ?, 'PRIVATE')",
                UUID.randomUUID(), adventureId, "the villain is the innkeeper", LocalDate.of(2026, 1, 1));
        stepDown();

        Assertions.assertFalse(call(HttpMethod.GET, "/adventure-notes?adventureId=" + adventureId, master.token(), null).getBody().contains("innkeeper"));
    }

    // ---------------------------------------------------------------- parallel helper

    private List<Integer> inParallel(List<java.util.concurrent.Callable<Integer>> actions) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (java.util.concurrent.Callable<Integer> action : actions) {
                results.add(pool.submit(() -> {
                    start.await();
                    return action.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
