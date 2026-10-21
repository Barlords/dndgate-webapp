package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A date sent by a client has to fit what the database can store (and what a person can mean): a year such as
 * +999999999 used to go through validation and end as a 500 when it reached PostgreSQL.
 */
class DateBoundsIntegrationTest extends ApiIntegrationTestSupport {

    private static final String TOO_FAR = "+999999999-12-31";

    private static final String TOO_OLD = "1800-01-01";

    private TestAccount master;

    private UUID adventureId;

    @BeforeEach
    void fixture() {
        master = registerAndLogin();
        adventureId = createAdventure(master, "PUBLIC");
    }

    private int status(HttpMethod method, String path, Object body) {
        return call(method, path, master.token(), body).getStatusCode().value();
    }

    @Test
    void a_planning_refuses_dates_that_no_calendar_means() {
        Assertions.assertEquals(400, status(HttpMethod.PUT, "/planning", Map.of("adventureId", adventureId.toString(), "propositionDates", List.of(TOO_FAR))));
        Assertions.assertEquals(400, status(HttpMethod.PUT, "/planning", Map.of("adventureId", adventureId.toString(), "propositionDates", List.of("2027-01-01", TOO_OLD))));
        Assertions.assertEquals(400, status(HttpMethod.POST, "/planning", Map.of("adventureId", adventureId.toString(), "propositionDates", List.of(TOO_FAR))));
    }

    @Test
    void an_availability_refuses_them_too() {
        Assertions.assertEquals(400, status(HttpMethod.PUT, "/planning/availability",
                Map.of("planningId", UUID.randomUUID().toString(), "propositionDates", List.of(TOO_FAR))));
    }

    @Test
    void ordinary_dates_are_still_accepted() {
        Assertions.assertEquals(201, status(HttpMethod.PUT, "/planning", Map.of("adventureId", adventureId.toString(), "propositionDates", List.of("2027-01-01", "2030-06-15"))));
    }

    @Test
    void the_dates_of_the_notes_are_bounded() {
        Assertions.assertEquals(400, status(HttpMethod.POST, "/adventure-notes",
                Map.of("adventureId", adventureId.toString(), "content", "note", "date", TOO_FAR, "visibility", "PUBLIC")));
        Assertions.assertEquals(400, status(HttpMethod.POST, "/historical-notes",
                Map.of("adventureId", adventureId.toString(), "content", "note", "date", TOO_OLD)));
        Assertions.assertEquals(201, status(HttpMethod.POST, "/historical-notes",
                Map.of("adventureId", adventureId.toString(), "content", "note", "date", "2026-05-01")));
    }

    @Test
    void the_date_of_a_note_cannot_be_changed_to_an_absurd_one() {
        String noteId = body(call(HttpMethod.POST, "/historical-notes", master.token(),
                Map.of("adventureId", adventureId.toString(), "content", "note", "date", "2026-05-01"))).get("id").asString();

        Assertions.assertEquals(400, status(HttpMethod.PATCH, "/historical-notes/" + noteId, Map.of("date", TOO_FAR)));
        Assertions.assertEquals(201, status(HttpMethod.PATCH, "/historical-notes/" + noteId, Map.of("date", "2026-06-01")));
    }
}
