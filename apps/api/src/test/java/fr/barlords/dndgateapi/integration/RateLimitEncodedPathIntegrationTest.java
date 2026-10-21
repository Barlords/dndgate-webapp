package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.bootstrap.DndGateApiApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;

/**
 * The limit must apply to the decoded path: Spring MVC decodes the path before choosing the route,
 * so a percent-encoded letter must not let a request slip past the per-address limit.
 * Own application context (distinct limits) so that the counters are not shared with other classes.
 */
@SpringBootTest(
        classes = DndGateApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "security.jwt.secret=integration-test-secret-integration-test-secret",
                "security.rate-limit.login.max=2",
                "security.rate-limit.login.window=PT1H",
                "security.rate-limit.refresh.max=2",
                "security.rate-limit.refresh.window=PT1H",
                "security.rate-limit.register.max=2",
                "security.rate-limit.register.window=PT1H",
                "security.rate-limit.verify.max=2",
                "security.rate-limit.verify.window=PT1H",
                "security.rate-limit.resend.max=2",
                "security.rate-limit.resend.window=PT1H"})
@ActiveProfiles("dev")
class RateLimitEncodedPathIntegrationTest extends ApiIntegrationTestSupport {

    private ResponseEntity<String> post(String encodedPath) {
        return client().method(HttpMethod.POST)
                .uri(URI.create("http://localhost:" + port + "/dndgate-api" + encodedPath))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .toEntity(String.class);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "login,/accounts/%6Cogin",
            "refresh,/accounts/%72efresh",
            "register,/%61ccounts",
            "verify,/accounts/%76erify",
            "resend,/accounts/%76erification-email",
            "confirm-email,/accounts/email/%63onfirm"})
    void a_percent_encoded_path_is_limited_like_the_plain_one(String rule, String encodedPath) {
        for (int i = 0; i < 2; i++) {
            Assertions.assertNotEquals(429, post(encodedPath).getStatusCode().value(), rule + " attempt " + i);
        }

        Assertions.assertEquals(429, post(encodedPath).getStatusCode().value(), rule);
    }
}
