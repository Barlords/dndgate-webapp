package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * What an operator can ask the running application: is it alive, can it serve, and what happened to a given request.
 * The probes live on their own port, which the reverse proxy does not publish.
 */
class ObservabilityIntegrationTest extends ApiIntegrationTestSupport {

    @LocalManagementPort
    int managementPort;

    private ResponseEntity<String> management(String path) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + managementPort)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                .build()
                .get().uri(path).retrieve().toEntity(String.class);
    }

    @Test
    void the_application_says_it_is_up_without_any_token() {
        ResponseEntity<String> response = management("/actuator/health");

        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals("UP", body(response).get("status").asString());
    }

    @Test
    void the_probes_tell_alive_from_ready() {
        Assertions.assertEquals(200, management("/actuator/health/liveness").getStatusCode().value());
        Assertions.assertEquals(200, management("/actuator/health/readiness").getStatusCode().value());
    }

    @Test
    void the_health_answer_does_not_describe_the_infrastructure() {
        JsonNode health = body(management("/actuator/health"));

        Assertions.assertFalse(health.has("components"), "no database or disk details for the first comer");
        Assertions.assertFalse(health.has("details"));
    }

    @Test
    void the_readiness_depends_on_the_database_but_not_on_the_mail_server() {
        // the mail server of the tests cannot be reached: if it counted, the application would never be ready
        Assertions.assertEquals(200, management("/actuator/health/readiness").getStatusCode().value());
        String readiness = management("/actuator/health/readiness").getBody();
        Assertions.assertFalse(readiness.contains("mail"), readiness);
    }

    @Test
    void metrics_are_available_for_the_collector() {
        ResponseEntity<String> response = management("/actuator/prometheus");

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertTrue(response.getBody().contains("jvm_memory_used_bytes"));
        Assertions.assertTrue(response.getBody().contains("http_server_requests"), "the requests are measured");
    }

    @Test
    void nothing_else_is_exposed() {
        for (String path : new String[]{"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/configprops"}) {
            // not exposed: refused (401 here, 404 elsewhere), never answered
            Assertions.assertTrue(management(path).getStatusCode().is4xxClientError(), path);
        }
    }

    @Test
    void the_probes_are_not_reachable_through_the_public_port() {
        ResponseEntity<String> response = call(org.springframework.http.HttpMethod.GET, "/actuator/health", null, null);

        Assertions.assertTrue(response.getStatusCode().value() == 401 || response.getStatusCode().value() == 404,
                String.valueOf(response.getStatusCode()));
        Assertions.assertFalse(String.valueOf(response.getBody()).contains("\"UP\""));
    }

    // ---- follow a request through the logs ----

    @Test
    void every_answer_carries_the_id_of_the_request_even_an_error() {
        ResponseEntity<String> unauthorized = call(org.springframework.http.HttpMethod.GET, "/accounts/me", null, null);

        Assertions.assertEquals(401, unauthorized.getStatusCode().value());
        Assertions.assertNotNull(unauthorized.getHeaders().getFirst("X-Request-Id"));
    }

    @Test
    void the_id_sent_by_the_front_comes_back() {
        ResponseEntity<String> response = client().get().uri("/accounts/me")
                .header("X-Request-Id", "front-abc-123")
                .retrieve().onStatus(HttpStatusCode::isError, (req, res) -> { }).toEntity(String.class);

        Assertions.assertEquals("front-abc-123", response.getHeaders().getFirst("X-Request-Id"));
    }
}
