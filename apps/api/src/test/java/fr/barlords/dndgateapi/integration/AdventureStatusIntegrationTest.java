package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

class AdventureStatusIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount master;

    private UUID adventureId;

    @BeforeEach
    void fixture() {
        master = registerAndLogin();
        adventureId = createAdventure(master, "PUBLIC");
    }

    private ResponseEntity<String> putStatus(String status) {
        return call(HttpMethod.PUT, "/adventures/" + adventureId + "/status", master.token(), Map.of("status", status));
    }

    private String storedStatus() {
        return jdbc.queryForObject("select status from adventure where id = ?", String.class, adventureId);
    }

    @Test
    void a_finished_adventure_cannot_be_reopened() {
        Assertions.assertEquals(200, putStatus("ONGOING").getStatusCode().value());
        Assertions.assertEquals(200, putStatus("FINISH").getStatusCode().value());

        ResponseEntity<String> refused = putStatus("NOT_STARTED");

        Assertions.assertEquals(409, refused.getStatusCode().value());
        Assertions.assertTrue(refused.getHeaders().getContentType().toString().contains("problem+json"));
        Assertions.assertTrue(refused.getBody().contains("FINISH") && refused.getBody().contains("NOT_STARTED"));
        Assertions.assertEquals("FINISH", storedStatus());
    }

    @Test
    void an_aborted_adventure_stays_aborted_and_a_started_one_does_not_go_back() {
        Assertions.assertEquals(200, putStatus("ONGOING").getStatusCode().value());
        Assertions.assertEquals(409, putStatus("NOT_STARTED").getStatusCode().value());
        Assertions.assertEquals(200, putStatus("ABORT").getStatusCode().value());

        Assertions.assertEquals(409, putStatus("ONGOING").getStatusCode().value());
        Assertions.assertEquals("ABORT", storedStatus());
    }

    @Test
    void an_adventure_cannot_finish_without_having_started() {
        Assertions.assertEquals(409, putStatus("FINISH").getStatusCode().value());
        Assertions.assertEquals("NOT_STARTED", storedStatus());
    }

    @Test
    void asking_for_the_current_status_again_is_accepted() {
        Assertions.assertEquals(200, putStatus("ONGOING").getStatusCode().value());
        Assertions.assertEquals(200, putStatus("ONGOING").getStatusCode().value());
        Assertions.assertEquals("ONGOING", storedStatus());
    }

    @Test
    void the_capacity_cannot_go_below_the_players_already_in() {
        TestAccount first = registerAndLogin();
        TestAccount second = registerAndLogin();
        for (TestAccount player : new TestAccount[]{first, second}) {
            Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null)
                    .getStatusCode().value());
        }

        ResponseEntity<String> refused = call(HttpMethod.PATCH, "/adventures/" + adventureId, master.token(), Map.of("maxPlayer", 1));

        Assertions.assertEquals(409, refused.getStatusCode().value());
        Assertions.assertTrue(refused.getHeaders().getContentType().toString().contains("problem+json"));
        Assertions.assertEquals(4, jdbc.queryForObject("select max_player from adventure where id = ?", Integer.class, adventureId));
        Assertions.assertEquals(201, call(HttpMethod.PATCH, "/adventures/" + adventureId, master.token(), Map.of("maxPlayer", 2))
                .getStatusCode().value());
    }

    @Test
    void a_patch_cannot_change_the_status() {
        Assertions.assertEquals(201, call(HttpMethod.PATCH, "/adventures/" + adventureId, master.token(),
                Map.of("name", "Renamed", "status", "FINISH")).getStatusCode().value());

        Assertions.assertEquals("NOT_STARTED", storedStatus());
    }
}
