package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.port.out.RatingPersistenceSpi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;

/**
 * {@code PUT /ratings} creates or updates. Two first ratings of the same account that cross each other: the second
 * request looks for the rating and finds none, the first one creates it, then the second one inserts and meets the
 * unique index. It is replayed and updates the rating instead of answering 409; {@code POST} keeps its 409.
 */
class RatingConcurrencyIntegrationTest extends ApiIntegrationTestSupport {

    @MockitoSpyBean
    RatingPersistenceSpi ratings;

    private TestAccount master;

    private TestAccount player;

    @AfterEach
    void restore() {
        Mockito.reset(ratings);
    }

    private ResponseEntity<String> rate(HttpMethod method, int rate) {
        return call(method, "/ratings", player.token(), Map.of("accountTargetedId", master.id().toString(), "rate", rate));
    }

    /** A player and a master who played together. */
    private void playedTogether() {
        master = registerAndLogin();
        player = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null)
                .getStatusCode().value());
        Assertions.assertEquals(200, call(HttpMethod.PUT, "/adventures/" + adventureId + "/status", master.token(),
                Map.of("status", "ONGOING")).getStatusCode().value());
    }

    /** Right after the next look for an existing rating, {@code meanwhile} runs to its end in another thread. */
    private void runRightAfterTheNextLookUp(Supplier<ResponseEntity<String>> meanwhile) {
        AtomicBoolean armed = new AtomicBoolean(true);
        Mockito.doAnswer(invocation -> {
            Object found = invocation.callRealMethod();
            if (armed.getAndSet(false)) {
                ResponseEntity<String> response = CompletableFuture.supplyAsync(meanwhile).get(60, TimeUnit.SECONDS);
                Assertions.assertTrue(response.getStatusCode().is2xxSuccessful(), response.getBody());
            }
            return found;
        }).when(ratings).findByEmitterIdAndTargetedAccountId(any(), any());
    }

    private int storedRate() {
        return jdbc.queryForObject("select rate from rating where emitter_id = ? and target_id = ?", Integer.class, player.id(), master.id());
    }

    @Test
    void two_first_puts_that_cross_each_other_both_succeed_and_leave_one_rating() {
        playedTogether();
        runRightAfterTheNextLookUp(() -> rate(HttpMethod.PUT, 2));

        ResponseEntity<String> response = rate(HttpMethod.PUT, 5);

        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from rating where emitter_id = ? and target_id = ?", Integer.class,
                player.id(), master.id()));
        Assertions.assertEquals(5, storedRate(), "the replayed request updated the rating");
    }

    @Test
    void two_first_posts_that_cross_each_other_still_give_409_to_the_second() {
        playedTogether();
        runRightAfterTheNextLookUp(() -> rate(HttpMethod.POST, 2));

        ResponseEntity<String> response = rate(HttpMethod.POST, 5);

        Assertions.assertEquals(409, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals(2, storedRate());
    }
}
