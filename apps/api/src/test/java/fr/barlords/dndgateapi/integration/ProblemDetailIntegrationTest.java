package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

/**
 * Every error the API answers has the same shape (RFC 9457 "problem details"), whoever produced it:
 * a controller, the validation, the framework, a servlet filter or the security chain.
 */
class ProblemDetailIntegrationTest extends ApiIntegrationTestSupport {

    private JsonNode problem(ResponseEntity<String> response, int expectedStatus) {
        Assertions.assertEquals(expectedStatus, response.getStatusCode().value(), response.getBody());
        MediaType type = response.getHeaders().getContentType();
        Assertions.assertNotNull(type, "an error has a body");
        Assertions.assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(type), String.valueOf(type));
        JsonNode body = body(response);
        Assertions.assertEquals(expectedStatus, body.get("status").asInt(), response.getBody());
        Assertions.assertFalse(body.get("title").asString().isBlank(), response.getBody());
        // whoever reports the error can quote this id: it is the one on every log line of the request
        Assertions.assertNotNull(response.getHeaders().getFirst("X-Request-Id"));
        Assertions.assertEquals(response.getHeaders().getFirst("X-Request-Id"), body.path("correlationId").asString(), response.getBody());
        return body;
    }

    @Test
    void the_id_sent_by_the_front_is_the_one_in_the_error() {
        ResponseEntity<String> response = client().get().uri("/no/such/route")
                .header("X-Request-Id", "front-abc-123")
                .retrieve().onStatus(org.springframework.http.HttpStatusCode::isError, (req, res) -> { }).toEntity(String.class);

        Assertions.assertEquals("front-abc-123", body(response).get("correlationId").asString());
    }

    @Test
    void a_business_error_is_a_problem_with_the_explanation_in_detail() {
        TestAccount account = registerAndLogin();

        JsonNode body = problem(call(HttpMethod.GET, "/adventures/" + UUID.randomUUID(), account.token(), null), 404);

        Assertions.assertEquals("Not Found", body.get("title").asString());
        Assertions.assertFalse(body.get("detail").asString().isBlank());
        Assertions.assertFalse(body.has("message"), "the old format is gone");
    }

    @Test
    void a_validation_error_lists_the_fields_in_errors() {
        JsonNode body = problem(call(HttpMethod.POST, "/accounts", null,
                Map.of("email", "not-an-email", "password", "x", "pseudo", "!")), 400);

        Assertions.assertTrue(body.get("errors").isArray());
        Assertions.assertTrue(body.get("errors").size() >= 1);
        JsonNode first = body.get("errors").get(0);
        Assertions.assertFalse(first.get("field").asString().isBlank());
        Assertions.assertFalse(first.get("message").asString().isBlank());
    }

    @Test
    void a_malformed_body_is_a_problem() {
        TestAccount account = registerAndLogin();

        problem(call(HttpMethod.POST, "/adventures", account.token(), "{not json"), 400);
    }

    @Test
    void an_unknown_route_is_a_problem() {
        TestAccount account = registerAndLogin();

        problem(call(HttpMethod.GET, "/no/such/route", account.token(), null), 404);
    }

    @Test
    void a_wrong_method_is_a_problem() {
        TestAccount account = registerAndLogin();

        problem(call(HttpMethod.PUT, "/accounts/me", account.token(), Map.of()), 405);
    }

    @Test
    void a_missing_token_is_a_problem_and_keeps_the_challenge_header() {
        ResponseEntity<String> response = call(HttpMethod.GET, "/accounts/me", null, null);

        problem(response, 401);
        Assertions.assertNotNull(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void a_refused_request_body_is_a_problem() {
        problem(call(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", "x", "password", "a".repeat(600_000))), 413);
    }

    @Test
    void a_locked_account_is_a_problem_with_retry_after() {
        TestAccount account = registerAndLogin();
        ResponseEntity<String> last = null;
        for (int i = 0; i < 6; i++) {
            last = call(HttpMethod.POST, "/accounts/login", null, Map.of("pseudo", account.pseudo(), "password", "wrong-password-1"));
        }

        problem(last, 429);
        Assertions.assertNotNull(last.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void an_unexpected_failure_is_a_500_problem_that_reveals_nothing() {
        Mockito.doThrow(new IllegalStateException("secret internal detail")).when(emailingSpi)
                .sendSignupConfirmationEmail(ArgumentMatchers.any(), ArgumentMatchers.any());
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        ResponseEntity<String> response = call(HttpMethod.POST, "/accounts", null,
                Map.of("email", "boom" + suffix + "@test.fr", "password", "password-" + suffix, "pseudo", "boom" + suffix));

        JsonNode body = problem(response, 500);
        Assertions.assertFalse(response.getBody().contains("secret internal detail"), response.getBody());
        Assertions.assertFalse(response.getBody().contains("IllegalStateException"), response.getBody());
        Assertions.assertEquals("Internal Server Error", body.get("title").asString());
    }
}
