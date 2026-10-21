package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The date of a message is the moment the server received it: a client can neither pin a message nor backdate it. */
class MessageSendDateIntegrationTest extends ApiIntegrationTestSupport {

    private JsonNode history(TestAccount account, UUID adventureId) {
        return body(call(HttpMethod.GET, "/messages?adventureId=" + adventureId, account.token(), null)).get("content");
    }

    private void send(TestAccount account, UUID adventureId, String content, Object... extra) {
        HashMap<String, Object> request = new HashMap<>(Map.of("adventureId", adventureId.toString(), "content", content));
        for (int i = 0; i < extra.length; i += 2) {
            request.put((String) extra[i], extra[i + 1]);
        }
        Assertions.assertEquals(200, call(HttpMethod.POST, "/messages", account.token(), request).getStatusCode().value());
    }

    @Test
    void a_send_date_chosen_by_the_client_is_ignored_and_the_server_date_is_used() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        Instant before = Instant.now().minusSeconds(1);

        send(master, adventureId, "forged", "sendDate", "1999-01-01T00:00:00Z");

        Instant after = Instant.now().plusSeconds(1);
        Instant stored = Instant.parse(history(master, adventureId).get(0).get("sendDate").asString());
        Assertions.assertFalse(stored.isBefore(before) || stored.isAfter(after), "stored " + stored);
    }

    @Test
    void a_message_cannot_be_pinned_at_the_top_with_a_date_in_the_far_future() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");

        send(master, adventureId, "pinned", "sendDate", "2199-12-31T00:00:00");
        send(master, adventureId, "later", "sendDate", "1999-01-01T00:00:00");

        JsonNode messages = history(master, adventureId);
        Assertions.assertEquals("later", messages.get(0).get("content").asString());
        Assertions.assertEquals("pinned", messages.get(1).get("content").asString());
    }

    @Test
    void the_stored_date_is_an_instant_with_a_time_zone() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        send(master, adventureId, "hello");

        Assertions.assertEquals("timestamp with time zone", jdbc.queryForObject(
                "select data_type from information_schema.columns where table_name = 'message' and column_name = 'send_date'", String.class));
        Assertions.assertTrue(history(master, adventureId).get(0).get("sendDate").asString().endsWith("Z"));
    }
}
