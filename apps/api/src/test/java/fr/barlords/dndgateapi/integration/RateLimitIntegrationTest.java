package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.bootstrap.DndGateApiApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

/**
 * Own application context with very low limits, so that it does not disturb the other tests
 * which log in and register accounts by the dozen from the same address.
 */
@SpringBootTest(
        classes = DndGateApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "security.jwt.secret=integration-test-secret-integration-test-secret",
                "security.rate-limit.login.max=3",
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
class RateLimitIntegrationTest extends ApiIntegrationTestSupport {

    private ResponseEntity<String> login(String pseudo) {
        return call(HttpMethod.POST, "/accounts/login", null, Map.of("pseudo", pseudo, "password", "whatever-password"));
    }

    @Test
    void login_attempts_are_limited_per_address() {
        for (int i = 0; i < 3; i++) {
            Assertions.assertEquals(401, login("nobody").getStatusCode().value());
        }

        ResponseEntity<String> refused = login("nobody");

        Assertions.assertEquals(429, refused.getStatusCode().value());
        Assertions.assertTrue(Long.parseLong(refused.getHeaders().getFirst("Retry-After")) > 0);
        Assertions.assertTrue(refused.getBody().contains("Too many requests"));
    }

    @Test
    void refresh_attempts_are_limited_independently_from_login() {
        for (int i = 0; i < 2; i++) {
            Assertions.assertEquals(401, call(HttpMethod.POST, "/accounts/refresh", null,
                    Map.of("refreshToken", "guess-" + i)).getStatusCode().value());
        }

        Assertions.assertEquals(429, call(HttpMethod.POST, "/accounts/refresh", null,
                Map.of("refreshToken", "guess-3")).getStatusCode().value());
    }

    @Test
    void account_creation_is_limited() {
        for (int i = 0; i < 2; i++) {
            String pseudo = "rate" + UUID.randomUUID().toString().substring(0, 8);
            Assertions.assertEquals(201, call(HttpMethod.POST, "/accounts", null,
                    Map.of("email", pseudo + "@test.fr", "password", "password123", "pseudo", pseudo)).getStatusCode().value());
        }

        Assertions.assertEquals(429, call(HttpMethod.POST, "/accounts", null,
                Map.of("email", "third@test.fr", "password", "password123", "pseudo", "third")).getStatusCode().value());
    }

    @Test
    void verification_code_attempts_are_limited() {
        for (int i = 0; i < 2; i++) {
            Assertions.assertEquals(400, verifyEmail("a@test.fr", "guess" + i));
        }

        Assertions.assertEquals(429, verifyEmail("a@test.fr", "guess3"));
    }

    @Test
    void address_change_code_attempts_are_limited() {
        for (int i = 0; i < 2; i++) {
            Assertions.assertEquals(400, call(HttpMethod.POST, "/accounts/email/confirm", null,
                    Map.of("email", "a@test.fr", "code", "guess" + i)).getStatusCode().value());
        }

        Assertions.assertEquals(429, call(HttpMethod.POST, "/accounts/email/confirm", null,
                Map.of("email", "a@test.fr", "code", "guess3")).getStatusCode().value());
    }

    @Test
    void asking_for_a_new_verification_mail_is_limited() {
        for (int i = 0; i < 2; i++) {
            Assertions.assertEquals(202, call(HttpMethod.POST, "/accounts/verification-email", null,
                    Map.of("email", "nobody" + i + "@test.fr")).getStatusCode().value());
        }

        Assertions.assertEquals(429, call(HttpMethod.POST, "/accounts/verification-email", null,
                Map.of("email", "nobody3@test.fr")).getStatusCode().value());
    }

    @Test
    void other_endpoints_are_not_limited() {
        for (int i = 0; i < 10; i++) {
            Assertions.assertEquals(401, call(HttpMethod.GET, "/accounts/me", null, null).getStatusCode().value());
        }
    }
}
