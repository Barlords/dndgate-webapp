package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An account has at most one availability vote per planning: voting again replaces the previous vote, and the
 * response always describes the vote that is really stored.
 */
class AvailabilityVoteIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount player;

    private UUID planningId;

    @BeforeEach
    void fixture() {
        TestAccount master = registerAndLogin();
        player = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        planningId = UUID.fromString(body(call(HttpMethod.POST, "/planning", master.token(), Map.of(
                "adventureId", adventureId.toString(), "propositionDates", List.of("2027-02-01", "2027-02-08", "2027-02-15"))))
                .get("id").asString());
    }

    private Map<String, Object> vote(String... dates) {
        return Map.of("planningId", planningId.toString(), "propositionDates", List.of(dates));
    }

    private long votes() {
        return jdbc.queryForObject("select count(*) from availability where planning_id = ?", Long.class, planningId);
    }

    @Test
    void voting_several_times_with_post_leaves_a_single_vote_holding_the_last_dates() {
        for (String date : List.of("2027-02-01", "2027-02-08", "2027-02-15")) {
            Assertions.assertEquals(201, call(HttpMethod.POST, "/planning/availability", player.token(), vote(date)).getStatusCode().value());
        }

        Assertions.assertEquals(1, votes());
        Assertions.assertEquals(List.of("2027-02-15"), jdbc.queryForList(
                "select d.availability_dates::text from availability_dates d join availability a on a.id = d.availability_id where a.planning_id = ?",
                String.class, planningId));
    }

    @Test
    void the_response_of_post_is_the_stored_vote() {
        String first = body(call(HttpMethod.POST, "/planning/availability", player.token(), vote("2027-02-01"))).get("id").asString();
        String second = body(call(HttpMethod.POST, "/planning/availability", player.token(), vote("2027-02-08"))).get("id").asString();

        Assertions.assertEquals(first, second);
        Assertions.assertEquals(first, jdbc.queryForObject("select id::text from availability where planning_id = ?", String.class, planningId));
    }

    @Test
    void the_response_of_put_is_the_stored_vote_on_creation_and_on_update() {
        String created = body(call(HttpMethod.PUT, "/planning/availability", player.token(), vote("2027-02-01"))).get("id").asString();
        Assertions.assertEquals(created, jdbc.queryForObject("select id::text from availability where planning_id = ?", String.class, planningId));

        var updated = body(call(HttpMethod.PUT, "/planning/availability", player.token(), vote("2027-02-08")));

        Assertions.assertEquals(created, updated.get("id").asString());
        Assertions.assertEquals(1, votes());
        Assertions.assertEquals(1, updated.get("availabilityDates").size());
    }

    @Test
    void post_then_put_still_make_a_single_vote() {
        call(HttpMethod.POST, "/planning/availability", player.token(), vote("2027-02-01"));
        call(HttpMethod.PUT, "/planning/availability", player.token(), vote("2027-02-08"));
        call(HttpMethod.POST, "/planning/availability", player.token(), vote("2027-02-15"));

        Assertions.assertEquals(1, votes());
    }

    @Test
    void the_database_itself_refuses_a_second_vote_of_the_same_account() {
        call(HttpMethod.POST, "/planning/availability", player.token(), vote("2027-02-01"));
        UUID accountId = jdbc.queryForObject("select account_id from availability where planning_id = ?", UUID.class, planningId);

        Assertions.assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbc.update(
                "insert into availability (id, planning_id, account_id) values (?, ?, ?)", UUID.randomUUID(), planningId, accountId));
    }

    @Test
    void two_accounts_each_keep_their_own_vote() {
        TestAccount other = registerAndLogin();
        call(HttpMethod.POST, "/planning/availability", player.token(), vote("2027-02-01"));
        call(HttpMethod.POST, "/planning/availability", other.token(), vote("2027-02-01"));
        call(HttpMethod.POST, "/planning/availability", other.token(), vote("2027-02-08"));

        Assertions.assertEquals(2, votes());
    }

    @Test
    void simultaneous_first_votes_of_one_account_all_succeed_and_leave_a_single_vote() throws Exception {
        int callers = 8;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(callers);
        var start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
        for (int i = 0; i < callers; i++) {
            String date = "2027-02-0" + (i + 1);
            results.add(pool.submit(() -> {
                start.await();
                return call(HttpMethod.POST, "/planning/availability", player.token(), vote(date)).getStatusCode().value();
            }));
        }
        start.countDown();
        for (var result : results) {
            Assertions.assertEquals(201, result.get());
        }
        pool.shutdown();

        Assertions.assertEquals(1, votes());
    }
}
