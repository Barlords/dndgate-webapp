package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A write that the database refuses because of the current state of the data is a 409, not a 500 without cause.
 */
class DatabaseConflictIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void a_second_planning_for_the_same_adventure_is_a_409_problem() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        Map<String, Object> request = Map.of("adventureId", adventureId.toString(), "propositionDates", List.of("2027-02-01"));
        Assertions.assertEquals(201, call(HttpMethod.POST, "/planning", master.token(), request).getStatusCode().value());

        ResponseEntity<String> second = call(HttpMethod.POST, "/planning", master.token(), request);

        Assertions.assertEquals(409, second.getStatusCode().value(), second.getBody());
        Assertions.assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(second.getHeaders().getContentType()));
        Assertions.assertEquals(409, body(second).get("status").asInt());
        Assertions.assertFalse(second.getBody().contains("planning_adventure_id_key"));
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from planning where adventure_id = ?", Long.class, adventureId));
    }
}
