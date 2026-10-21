package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

/**
 * What the log collector really receives in production: one JSON object per line, with the audit fields
 * and the identifier of the request that caused them, so that a deletion can be traced back to its request.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "logging.structured.format.console=logstash")
class AuditLogFormatIntegrationTest extends ApiIntegrationTestSupport {

    @Test
    void a_deletion_reaches_the_collector_as_one_json_line_with_the_id_of_its_request(CapturedOutput output) {
        TestAccount owner = registerAndLogin();
        UUID bookId = UUID.fromString(body(call(HttpMethod.POST, "/spellbooks", owner.token(),
                Map.of("name", "Fireballs", "description", "d"))).get("id").asString());

        ResponseEntity<String> response = client().method(HttpMethod.DELETE).uri("/spellbooks/" + bookId)
                .header("Authorization", "Bearer " + owner.token())
                .header("X-Request-Id", "audit-format-test-1")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("confirmation", "Fireballs"))
                .retrieve().toEntity(String.class);
        Assertions.assertEquals(204, response.getStatusCode().value());

        String line = output.getOut().lines()
                .filter(l -> l.contains("\"event\":\"deletion\"") && l.contains(bookId.toString()))
                .findFirst().orElseThrow(() -> new AssertionError("no audit line in:\n" + output.getOut()));
        JsonNode json = this.json.readTree(line);
        Assertions.assertEquals("AUDIT", json.get("logger_name").asString());
        Assertions.assertEquals("spellbook", json.get("subject").asString());
        Assertions.assertEquals(owner.id().toString(), json.get("actorId").asString());
        Assertions.assertEquals(bookId.toString(), json.get("subjectId").asString());
        Assertions.assertEquals("audit-format-test-1", json.get("correlationId").asString());
        Assertions.assertFalse(line.contains("Fireballs"), "the name is not written");
    }
}
