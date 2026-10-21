package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureConcurrentModificationException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

class AdventureConcurrencyIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    private AdventurePersistenceSpi adventures;

    private TestAccount master;

    @BeforeEach
    void fixture() {
        master = registerAndLogin();
    }

    private UUID createAdventure(int maxPlayer) {
        ResponseEntity<String> response = call(HttpMethod.POST, "/adventures", master.token(), Map.of(
                "name", "Quest", "description", "A quest", "maxPlayer", maxPlayer,
                "visibility", "PUBLIC", "postalCode", "75001", "city", "Paris"));
        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
        return UUID.fromString(body(response).get("id").asString());
    }

    /** All the requests leave at the same moment. */
    private List<Integer> inParallel(List<TestAccount> accounts, UUID adventureId, HttpMethod method, String action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(accounts.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (TestAccount account : accounts) {
                results.add(pool.submit(() -> {
                    start.await();
                    return call(method, "/adventures/" + adventureId + "/" + action, account.token(), null)
                            .getStatusCode().value();
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

    private List<TestAccount> accounts(int count) {
        List<TestAccount> accounts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            accounts.add(registerAndLogin());
        }
        return accounts;
    }

    private List<UUID> players(UUID adventureId) {
        return jdbc.queryForList("select player_id from adventure_players where adventure_id = ?", UUID.class, adventureId);
    }

    // ---- the capacity holds when everybody joins at once ----

    @Test
    void players_joining_at_the_same_moment_never_exceed_the_capacity() throws Exception {
        UUID adventureId = createAdventure(3);
        List<TestAccount> candidates = accounts(8);

        List<Integer> statuses = inParallel(candidates, adventureId, HttpMethod.POST, "join");

        Assertions.assertEquals(3, statuses.stream().filter(s -> s == 200).count(), statuses.toString());
        // the adventure turns private as soon as it is full: late players find it full (409) or no longer see it (404)
        Assertions.assertEquals(5, statuses.stream().filter(s -> s == 409 || s == 404).count(), statuses.toString());
        Assertions.assertEquals(3, players(adventureId).size());
        Assertions.assertEquals(3, players(adventureId).stream().distinct().count());
        Assertions.assertEquals("ONGOING", jdbc.queryForObject("select status from adventure where id = ?", String.class, adventureId));
    }

    @Test
    void nobody_is_lost_when_several_players_join_an_adventure_with_room() throws Exception {
        UUID adventureId = createAdventure(10);
        List<TestAccount> candidates = accounts(6);

        List<Integer> statuses = inParallel(candidates, adventureId, HttpMethod.POST, "join");

        Assertions.assertTrue(statuses.stream().allMatch(s -> s == 200), statuses.toString());
        Assertions.assertEquals(6, players(adventureId).size());
    }

    @Test
    void the_same_player_clicking_many_times_joins_once() throws Exception {
        UUID adventureId = createAdventure(5);
        TestAccount player = registerAndLogin();

        List<Integer> statuses = inParallel(List.of(player, player, player, player), adventureId, HttpMethod.POST, "join");

        Assertions.assertEquals(1, statuses.stream().filter(s -> s == 200).count(), statuses.toString());
        Assertions.assertEquals(1, players(adventureId).size());
    }

    @Test
    void players_who_leave_at_the_same_moment_are_all_removed() throws Exception {
        UUID adventureId = createAdventure(10);
        List<TestAccount> candidates = accounts(5);
        for (TestAccount candidate : candidates) {
            Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", candidate.token(), null)
                    .getStatusCode().value());
        }

        List<Integer> statuses = inParallel(candidates, adventureId, HttpMethod.DELETE, "players/me");

        Assertions.assertTrue(statuses.stream().allMatch(s -> s == 200), statuses.toString());
        Assertions.assertTrue(players(adventureId).isEmpty());
    }

    // ---- a stale write is refused ----

    @Test
    void a_write_made_from_a_stale_read_is_refused_instead_of_overwriting() {
        UUID adventureId = createAdventure(4);
        Adventure first = adventures.findById(adventureId).orElseThrow();
        Adventure second = adventures.findById(adventureId).orElseThrow();

        adventures.save(first.withName("Renamed by the first")).get();

        Assertions.assertThrows(AdventureConcurrentModificationException.class,
                () -> adventures.save(second.withStatus(AdventureStatus.FINISH)));
        Assertions.assertEquals("Renamed by the first", jdbc.queryForObject("select name from adventure where id = ?", String.class, adventureId));
        Assertions.assertEquals("NOT_STARTED", jdbc.queryForObject("select status from adventure where id = ?", String.class, adventureId));
    }

    @Test
    void every_save_moves_the_version_forward() {
        UUID adventureId = createAdventure(4);
        Adventure read = adventures.findById(adventureId).orElseThrow();

        Adventure saved = adventures.save(read.withName("Another name")).get();

        Assertions.assertEquals(read.getVersion() + 1, saved.getVersion());
        Assertions.assertEquals(saved.getVersion(), adventures.findById(adventureId).orElseThrow().getVersion());
    }

    @Test
    void joining_changes_the_version_even_though_only_the_list_of_players_changes() {
        UUID adventureId = createAdventure(4);
        Long before = adventures.findById(adventureId).orElseThrow().getVersion();

        call(HttpMethod.POST, "/adventures/" + adventureId + "/join", registerAndLogin().token(), null);

        Assertions.assertTrue(adventures.findById(adventureId).orElseThrow().getVersion() > before);
    }

    // ---- the shape of the aggregate ----

    @Test
    void an_adventure_no_longer_embeds_the_characters_and_the_notes() {
        UUID adventureId = createAdventure(4);

        JsonNode adventure = body(call(HttpMethod.GET, "/adventures/" + adventureId, master.token(), null));

        Assertions.assertFalse(adventure.has("gameCharacters"));
        Assertions.assertFalse(adventure.has("historicalNotes"));
        Assertions.assertFalse(adventure.has("adventureNotes"));
        Assertions.assertFalse(adventure.has("version"), "the version is not part of the contract");
    }

    @Test
    void the_redundant_join_tables_are_gone() {
        for (String table : List.of("adventure_game_characters", "adventure_historical_notes", "adventure_adventure_notes")) {
            Assertions.assertEquals(0, jdbc.queryForObject(
                    "select count(*) from information_schema.tables where table_name = ?", Integer.class, table), table);
        }
    }
}
