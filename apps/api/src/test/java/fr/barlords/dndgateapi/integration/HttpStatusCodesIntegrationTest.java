package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Status codes follow the verb: a PATCH answers 200, a PUT answers 201 when it creates and 200 when it updates, a POST
 * that creates answers 201 with the created resource, a mail sent later answers 202, a failed login answers 401.
 */
class HttpStatusCodesIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount master;

    private TestAccount player;

    private UUID adventureId;

    @BeforeEach
    void fixture() {
        master = registerAndLogin();
        player = registerAndLogin();
        adventureId = createAdventure(master, "PUBLIC");
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null)
                .getStatusCode().value());
    }

    private int status(TestAccount account, HttpMethod method, String path, Object body) {
        return call(method, path, account.token(), body).getStatusCode().value();
    }

    // ---- PATCH answers 200 ----

    @Test
    void a_patch_answers_200_for_an_adventure_a_character_and_a_spellbook() {
        Assertions.assertEquals(200, status(master, HttpMethod.PATCH, "/adventures/" + adventureId, Map.of("name", "Renamed")));

        UUID characterId = UUID.fromString(body(createCharacter(master, adventureId, "Gandalf")).get("id").asString());
        Assertions.assertEquals(200, status(master, HttpMethod.PATCH, "/game-characters/" + characterId, Map.of("race", "Maia")));

        ResponseEntity<String> book = call(HttpMethod.POST, "/spellbooks", master.token(),
                Map.of("name", "Book", "description", "Spells", "ruleset", "dnd5e-2014", "spellList", List.of()));
        Assertions.assertEquals(201, book.getStatusCode().value(), book.getBody());
        String bookId = body(book).get("id").asString();
        Assertions.assertEquals(200, status(master, HttpMethod.PATCH, "/spellbooks/" + bookId,
                Map.of("name", "Book 2", "description", "Other")));
    }

    @Test
    void a_patch_answers_200_for_both_kinds_of_notes() {
        UUID noteId = UUID.randomUUID();
        jdbc.update("insert into adventure_note (id, adventure_id, content, date, visibility) values (?, ?, ?, ?, ?)",
                noteId, adventureId, "first", LocalDate.of(2026, 1, 1), "PRIVATE");
        UUID historyId = UUID.randomUUID();
        jdbc.update("insert into historical_note (id, adventure_id, content, date) values (?, ?, ?, ?)",
                historyId, adventureId, "chapter", LocalDate.of(2026, 1, 1));

        Assertions.assertEquals(200, status(master, HttpMethod.PATCH, "/adventure-notes/" + noteId, Map.of("content", "second")));
        Assertions.assertEquals(200, status(master, HttpMethod.PATCH, "/historical-notes/" + historyId, Map.of("content", "two")));
    }

    // ---- PUT: 201 when it creates, 200 when it updates ----

    @Test
    void put_planning_answers_201_then_200() {
        Map<String, Object> request = Map.of("adventureId", adventureId.toString(), "propositionDates", List.of("2027-02-01"));

        Assertions.assertEquals(201, status(master, HttpMethod.PUT, "/planning", request));
        Assertions.assertEquals(200, status(master, HttpMethod.PUT, "/planning",
                Map.of("adventureId", adventureId.toString(), "propositionDates", List.of("2027-02-08"))));
    }

    @Test
    void put_availability_answers_201_then_200() {
        String planningId = body(call(HttpMethod.POST, "/planning", master.token(), Map.of(
                "adventureId", adventureId.toString(), "propositionDates", List.of("2027-02-01", "2027-02-08")))).get("id").asString();

        Assertions.assertEquals(201, status(player, HttpMethod.PUT, "/planning/availability",
                Map.of("planningId", planningId, "propositionDates", List.of("2027-02-01"))));
        Assertions.assertEquals(200, status(player, HttpMethod.PUT, "/planning/availability",
                Map.of("planningId", planningId, "propositionDates", List.of("2027-02-08"))));
    }

    @Test
    void put_rating_answers_201_then_200() {
        Assertions.assertEquals(200, status(master, HttpMethod.PUT, "/adventures/" + adventureId + "/status", Map.of("status", "ONGOING")));
        Map<String, Object> request = Map.of("accountTargetedId", master.id().toString(), "rate", 4);

        Assertions.assertEquals(201, status(player, HttpMethod.PUT, "/ratings", request));
        Assertions.assertEquals(200, status(player, HttpMethod.PUT, "/ratings", Map.of("accountTargetedId", master.id().toString(), "rate", 2)));
    }

    // ---- POST that creates ----

    @Test
    void sending_a_message_answers_201_with_the_created_message() {
        ResponseEntity<String> response = call(HttpMethod.POST, "/messages", player.token(),
                Map.of("adventureId", adventureId.toString(), "content", "hello"));

        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals("hello", body(response).get("content").asString());
        Assertions.assertEquals(player.id().toString(), body(response).get("senderId").asString());
        Assertions.assertEquals(adventureId.toString(), body(response).get("adventureId").asString());
        Assertions.assertEquals(body(response).get("id").asString(),
                jdbc.queryForObject("select id::text from message where adventure_id = ?", String.class, adventureId));
    }

    @Test
    void inviting_a_player_answers_202_because_the_mail_leaves_later() {
        TestAccount invited = registerAndLogin();

        Assertions.assertEquals(202, status(master, HttpMethod.POST, "/adventures/" + adventureId + "/invitations/" + invited.pseudo(), null));
    }

    // ---- login failures answer 401 ----

    @Test
    void a_failed_login_answers_401_for_an_unknown_pseudo_and_for_a_wrong_password() {
        Assertions.assertEquals(401, call(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", "nobody" + UUID.randomUUID().toString().substring(0, 8), "password", "whatever")).getStatusCode().value());
        Assertions.assertEquals(401, call(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", master.pseudo(), "password", "wrong-password")).getStatusCode().value());
    }
}
