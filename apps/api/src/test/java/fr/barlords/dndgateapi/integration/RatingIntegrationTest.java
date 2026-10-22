package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.functional.exception.rating.RatingAlreadyExistsException;
import fr.barlords.dndgateapi.domain.functional.model.Rating;
import fr.barlords.dndgateapi.domain.port.out.RatingPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

/**
 * An account can only rate another account it has played an adventure with.
 * Fixture: a PUBLIC adventure with a game master and a player who joined, and a stranger.
 */
class RatingIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount master;

    private TestAccount player;

    private TestAccount stranger;

    private UUID adventureId;

    @BeforeEach
    void fixture() {
        master = registerAndLogin();
        player = registerAndLogin();
        stranger = registerAndLogin();
        adventureId = createAdventure(master, "PUBLIC");

        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null)
                .getStatusCode().value());
    }

    @Autowired
    private RatingPersistenceSpi ratings;

    private void startAdventure() {
        Assertions.assertEquals(200, call(HttpMethod.PUT, "/adventures/" + adventureId + "/status", master.token(),
                Map.of("status", "ONGOING")).getStatusCode().value());
    }

    private ResponseEntity<String> rate(TestAccount emitter, TestAccount target, int rate) {
        return call(HttpMethod.POST, "/ratings", emitter.token(),
                Map.of("accountTargetedId", target.id().toString(), "rate", rate));
    }

    // ---- who can rate whom ----

    @Test
    void nobody_can_rate_before_the_adventure_has_started() {
        Assertions.assertEquals(403, rate(player, master, 5).getStatusCode().value());
        Assertions.assertEquals(403, rate(master, player, 5).getStatusCode().value());
    }

    @Test
    void participants_can_rate_each_other_once_the_adventure_is_started() {
        startAdventure();

        Assertions.assertEquals(201, rate(player, master, 4).getStatusCode().value());
        Assertions.assertEquals(201, rate(master, player, 5).getStatusCode().value());
    }

    @Test
    void a_stranger_cannot_rate_participants_and_cannot_be_rated() {
        startAdventure();

        Assertions.assertEquals(403, rate(stranger, master, 5).getStatusCode().value());
        Assertions.assertEquals(403, rate(stranger, player, 1).getStatusCode().value());
        Assertions.assertEquals(403, rate(player, stranger, 1).getStatusCode().value());
    }

    @Test
    void rating_oneself_is_refused() {
        startAdventure();

        Assertions.assertEquals(400, rate(master, master, 5).getStatusCode().value());
        Assertions.assertEquals(400, call(HttpMethod.PUT, "/ratings", player.token(),
                Map.of("accountTargetedId", player.id().toString(), "rate", 5)).getStatusCode().value());
    }

    @Test
    void the_same_account_cannot_be_rated_twice_with_post() {
        startAdventure();

        Assertions.assertEquals(201, rate(player, master, 3).getStatusCode().value());
        Assertions.assertEquals(409, rate(player, master, 5).getStatusCode().value());

        // the average was not inflated by the refused attempt
        Assertions.assertEquals("{\"average\":3.0,\"count\":1}", call(HttpMethod.GET, "/ratings/mean?accountId=" + master.id(),
                player.token(), null).getBody());
    }

    @Test
    void put_creates_then_updates_the_same_rating_and_is_also_restricted() {
        Assertions.assertEquals(403, call(HttpMethod.PUT, "/ratings", player.token(),
                Map.of("accountTargetedId", master.id().toString(), "rate", 5)).getStatusCode().value());

        startAdventure();
        call(HttpMethod.PUT, "/ratings", player.token(), Map.of("accountTargetedId", master.id().toString(), "rate", 2));
        call(HttpMethod.PUT, "/ratings", player.token(), Map.of("accountTargetedId", master.id().toString(), "rate", 4));

        Assertions.assertEquals("{\"average\":4.0,\"count\":1}", call(HttpMethod.GET, "/ratings/mean?accountId=" + master.id(),
                stranger.token(), null).getBody());
    }

    @Test
    void the_rate_must_be_between_1_and_5() {
        startAdventure();

        Assertions.assertEquals(400, rate(player, master, 6).getStatusCode().value());
        Assertions.assertEquals(400, rate(player, master, 0).getStatusCode().value());
    }

    // ---- who can read / change what ----

    @Test
    void the_detail_of_received_ratings_is_only_readable_by_the_rated_account() {
        startAdventure();
        rate(player, master, 4);

        String path = "/ratings?targetedAccountId=" + master.id();
        Assertions.assertEquals(200, call(HttpMethod.GET, path, master.token(), null).getStatusCode().value());
        Assertions.assertEquals(403, call(HttpMethod.GET, path, player.token(), null).getStatusCode().value());
        Assertions.assertEquals(403, call(HttpMethod.GET, path, stranger.token(), null).getStatusCode().value());
    }

    @Test
    void a_caller_can_only_read_the_ratings_it_gave() {
        startAdventure();
        rate(player, master, 4);

        String own = "/ratings/between?emitterId=" + player.id() + "&targetedAccountId=" + master.id();
        ResponseEntity<String> found = call(HttpMethod.GET, own, player.token(), null);
        Assertions.assertEquals(200, found.getStatusCode().value());
        Assertions.assertEquals(4, body(found).get("rate").asInt());

        // someone else asking for the ratings given by the player
        Assertions.assertEquals(403, call(HttpMethod.GET, own, stranger.token(), null).getStatusCode().value());
        Assertions.assertEquals(403, call(HttpMethod.GET, own, master.token(), null).getStatusCode().value());

        // nothing given from the master to the player yet
        String missing = "/ratings/between?emitterId=" + master.id() + "&targetedAccountId=" + player.id();
        Assertions.assertEquals(404, call(HttpMethod.GET, missing, master.token(), null).getStatusCode().value());
    }

    @Test
    void a_second_rating_slipping_past_the_check_is_stopped_by_the_database() {
        // what two simultaneous requests do: both found no rating, both write
        ratings.save(Rating.builder().emitterId(player.id()).accountTargetedId(master.id()).rate(4).build());

        Assertions.assertThrows(RatingAlreadyExistsException.class,
                () -> ratings.save(Rating.builder().emitterId(player.id()).accountTargetedId(master.id()).rate(2).build()));

        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from rating where emitter_id = ? and target_id = ?",
                Integer.class, player.id(), master.id()));
    }

    @Test
    void the_average_is_public_to_any_authenticated_account() {
        startAdventure();
        rate(player, master, 5);

        Assertions.assertEquals(200, call(HttpMethod.GET, "/ratings/mean?accountId=" + master.id(),
                stranger.token(), null).getStatusCode().value());
        Assertions.assertEquals(401, call(HttpMethod.GET, "/ratings/mean?accountId=" + master.id(),
                null, null).getStatusCode().value());
    }
}
