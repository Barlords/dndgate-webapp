package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The history of an adventure holds at most what the plan of its game master allows; the oldest messages go first. */
class MessageHistoryIntegrationTest extends ApiIntegrationTestSupport {

    private List<String> history(TestAccount account, UUID adventureId) {
        List<String> contents = new ArrayList<>();
        for (JsonNode message : body(call(HttpMethod.GET, "/messages?adventureId=" + adventureId, account.token(), null)).get("content")) {
            contents.add(message.get("content").asString());
        }
        return contents;
    }

    private void send(TestAccount account, UUID adventureId, String content) {
        Assertions.assertEquals(201, call(HttpMethod.POST, "/messages", account.token(),
                Map.of("adventureId", adventureId.toString(), "content", content)).getStatusCode().value());
    }

    @Test
    void the_fourth_message_removes_the_first() {
        TestAccount master = registerAndLogin();
        subscribe(master, "test-chat");
        UUID adventureId = createAdventure(master, "PUBLIC");

        for (String content : List.of("one", "two", "three")) {
            send(master, adventureId, content);
        }
        Assertions.assertEquals(List.of("three", "two", "one"), history(master, adventureId));

        send(master, adventureId, "four");

        Assertions.assertEquals(List.of("four", "three", "two"), history(master, adventureId));
    }

    @Test
    void after_a_downgrade_the_next_message_trims_the_history_to_the_new_limit() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        for (String content : List.of("one", "two", "three", "four", "five")) {
            send(master, adventureId, content);
        }

        subscribe(master, "test-chat");
        Assertions.assertEquals(5, history(master, adventureId).size());

        send(master, adventureId, "six");

        Assertions.assertEquals(List.of("six", "five", "four"), history(master, adventureId));
    }

    @Test
    void the_history_of_another_adventure_is_left_alone() {
        TestAccount master = registerAndLogin();
        subscribe(master, "test-chat");
        UUID busy = createAdventure(master, "PUBLIC");
        UUID quiet = createAdventure(master, "PUBLIC");
        send(master, quiet, "kept");

        for (int i = 0; i < 5; i++) {
            send(master, busy, "m" + i);
        }

        Assertions.assertEquals(List.of("kept"), history(master, quiet));
        Assertions.assertEquals(3, history(master, busy).size());
    }
}
