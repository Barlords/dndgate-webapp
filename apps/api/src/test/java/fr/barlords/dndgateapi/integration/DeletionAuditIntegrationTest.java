package fr.barlords.dndgateapi.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every confirmed deletion leaves a trace saying who deleted what, and when (the time of the log line).
 * Only identifiers are written: the logs must not become a second copy of the personal data that was just deleted.
 */
class DeletionAuditIntegrationTest extends ApiIntegrationTestSupport {

    private final ListAppender<ILoggingEvent> audit = new ListAppender<>();

    private Logger auditLogger;

    @BeforeEach
    void listenToTheAuditLog() {
        auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
        audit.start();
        auditLogger.addAppender(audit);
    }

    @AfterEach
    void stopListening() {
        auditLogger.detachAppender(audit);
        audit.stop();
        audit.list.clear();
    }

    private int delete(String path, TestAccount account, Object body) {
        return call(HttpMethod.DELETE, path, account.token(), body).getStatusCode().value();
    }

    private Map<String, String> field(ILoggingEvent event) {
        Map<String, String> values = new java.util.HashMap<>();
        for (KeyValuePair pair : event.getKeyValuePairs()) {
            values.put(pair.key, String.valueOf(pair.value));
        }
        return values;
    }

    /** The audit lines written for this actor, so that tests never read each other's lines. */
    private List<Map<String, String>> auditedBy(TestAccount actor) {
        return List.copyOf(audit.list).stream()
                .map(this::field)
                .filter(values -> actor.id().toString().equals(values.get("actorId")))
                .toList();
    }

    private void assertOneDeletion(TestAccount actor, String subject, UUID subjectId) {
        List<Map<String, String>> lines = auditedBy(actor);
        Assertions.assertEquals(1, lines.size(), String.valueOf(lines));
        Assertions.assertEquals("deletion", lines.get(0).get("event"));
        Assertions.assertEquals(subject, lines.get(0).get("subject"));
        Assertions.assertEquals(subjectId.toString(), lines.get(0).get("subjectId"));
    }

    // ---- each kind of deletion ----

    @Test
    void deleting_an_account_is_traced() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(204, deleteAccount(account));

        assertOneDeletion(account, "account", account.id());
    }

    @Test
    void deleting_an_adventure_is_traced() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");

        Assertions.assertEquals(204, delete("/adventures/" + adventureId, master, Map.of("confirmation", "Quest")));

        assertOneDeletion(master, "adventure", adventureId);
    }

    @Test
    void deleting_a_character_is_traced() {
        TestAccount master = registerAndLogin();
        TestAccount player = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null);
        UUID characterId = UUID.fromString(body(createCharacter(player, adventureId, "Gandalf")).get("id").asString());

        Assertions.assertEquals(204, delete("/game-characters/" + characterId, player, Map.of("confirmation", "Gandalf")));

        assertOneDeletion(player, "character", characterId);
    }

    @Test
    void deleting_a_spell_book_is_traced() {
        TestAccount owner = registerAndLogin();
        UUID bookId = UUID.fromString(body(call(HttpMethod.POST, "/spellbooks", owner.token(),
                Map.of("name", "Fireballs", "description", "d", "ruleset", "dnd5e-2014"))).get("id").asString());

        Assertions.assertEquals(204, delete("/spellbooks/" + bookId, owner, Map.of("confirmation", "Fireballs")));

        assertOneDeletion(owner, "spellbook", bookId);
    }

    @Test
    void deleting_a_note_of_the_game_master_is_traced() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        UUID noteId = UUID.fromString(body(call(HttpMethod.POST, "/adventure-notes", master.token(), Map.of(
                "adventureId", adventureId.toString(), "content", "n", "date", "2026-01-01", "visibility", "PUBLIC"))).get("id").asString());

        Assertions.assertEquals(204, delete("/adventure-notes/" + noteId, master, Map.of("confirm", true)));

        assertOneDeletion(master, "adventure-note", noteId);
    }

    @Test
    void deleting_a_historical_note_is_traced() {
        TestAccount master = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");
        UUID noteId = UUID.fromString(body(call(HttpMethod.POST, "/historical-notes", master.token(), Map.of(
                "adventureId", adventureId.toString(), "content", "h", "date", "2026-01-01"))).get("id").asString());

        Assertions.assertEquals(204, delete("/historical-notes/" + noteId, master, Map.of("confirm", true)));

        assertOneDeletion(master, "historical-note", noteId);
    }

    // ---- what is not traced ----

    @Test
    void a_refused_deletion_leaves_no_trace_of_a_deletion() {
        TestAccount master = registerAndLogin();
        TestAccount stranger = registerAndLogin();
        UUID adventureId = createAdventure(master, "PUBLIC");

        Assertions.assertEquals(400, delete("/adventures/" + adventureId, master, Map.of("confirmation", "wrong name")));
        Assertions.assertEquals(400, delete("/adventures/" + adventureId, master, null));
        Assertions.assertEquals(403, delete("/adventures/" + adventureId, stranger, Map.of("confirmation", "Quest")));
        Assertions.assertEquals(403, delete("/accounts", stranger, Map.of("confirmation", "not-the-password")));

        Assertions.assertTrue(auditedBy(master).isEmpty(), String.valueOf(auditedBy(master)));
        Assertions.assertTrue(auditedBy(stranger).isEmpty(), String.valueOf(auditedBy(stranger)));
    }

    @Test
    void the_trace_holds_identifiers_only_never_names_or_addresses() {
        TestAccount owner = registerAndLogin();
        UUID bookId = UUID.fromString(body(call(HttpMethod.POST, "/spellbooks", owner.token(),
                Map.of("name", "SecretGrimoireName", "description", "d", "ruleset", "dnd5e-2014"))).get("id").asString());
        delete("/spellbooks/" + bookId, owner, Map.of("confirmation", "SecretGrimoireName"));
        deleteAccount(owner);

        String everything = audit.list.stream()
                .filter(event -> auditedBy(owner).stream().anyMatch(values -> values.equals(field(event))))
                .map(event -> event.getFormattedMessage() + " " + field(event))
                .reduce("", (a, b) -> a + "\n" + b);

        Assertions.assertFalse(everything.isBlank(), "the lines exist");
        Assertions.assertFalse(everything.contains("SecretGrimoireName"), everything);
        Assertions.assertFalse(everything.contains(owner.pseudo()), everything);
        Assertions.assertFalse(everything.contains("@test.fr"), everything);
    }
}
