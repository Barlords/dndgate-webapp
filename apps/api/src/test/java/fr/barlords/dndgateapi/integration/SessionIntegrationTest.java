package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

class SessionIntegrationTest extends ApiIntegrationTestSupport {

    private int status(HttpMethod method, String path, String token, Object body) {
        return call(method, path, token, body).getStatusCode().value();
    }

    private ResponseEntity<String> refresh(String refreshToken) {
        return call(HttpMethod.POST, "/accounts/refresh", null, Map.of("refreshToken", refreshToken));
    }

    // ---- login / refresh ----

    @Test
    void login_returns_a_short_lived_access_token_and_a_refresh_token() {
        TestAccount account = registerAndLogin();

        ResponseEntity<String> login = call(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", account.pseudo(), "password", account.password()));
        JsonNode node = body(login);

        Instant accessExpiry = Instant.parse(node.get("expiresAt").asString());
        Instant refreshExpiry = Instant.parse(node.get("refreshTokenExpiresAt").asString());
        Assertions.assertTrue(accessExpiry.isBefore(Instant.now().plus(Duration.ofMinutes(16))), "access token must be short lived");
        Assertions.assertTrue(refreshExpiry.isAfter(Instant.now().plus(Duration.ofDays(59))), "refresh token must outlive the usual gap between two play sessions");
        Assertions.assertFalse(node.get("refreshToken").asString().isBlank());
    }

    @Test
    void a_refresh_token_gives_a_new_working_access_token() {
        TestAccount account = registerAndLogin();

        ResponseEntity<String> refreshed = refresh(account.refreshToken());

        Assertions.assertEquals(200, refreshed.getStatusCode().value(), refreshed.getBody());
        String newAccessToken = body(refreshed).get("token").asString();
        Assertions.assertNotEquals(account.token(), newAccessToken);
        Assertions.assertEquals(200, status(HttpMethod.GET, "/accounts/me", newAccessToken, null));
        Assertions.assertNotEquals(account.refreshToken(), body(refreshed).get("refreshToken").asString());
    }

    @Test
    void an_unknown_or_missing_refresh_token_is_refused() {
        Assertions.assertEquals(401, refresh("not-a-refresh-token").getStatusCode().value());
        Assertions.assertEquals(400, call(HttpMethod.POST, "/accounts/refresh", null, Map.of("refreshToken", "")).getStatusCode().value());
    }

    @Test
    void a_refresh_token_can_only_be_used_once_and_reusing_it_ends_the_session() {
        TestAccount account = registerAndLogin();
        JsonNode renewed = body(refresh(account.refreshToken()));
        String newAccess = renewed.get("token").asString();
        String newRefresh = renewed.get("refreshToken").asString();

        // somebody replays the first refresh token: it was stolen
        Assertions.assertEquals(401, refresh(account.refreshToken()).getStatusCode().value());

        // the whole session is dead, including what the legitimate chain just received
        Assertions.assertEquals(401, status(HttpMethod.GET, "/accounts/me", newAccess, null));
        Assertions.assertEquals(401, refresh(newRefresh).getStatusCode().value());
    }

    @Test
    void refreshing_can_be_chained() {
        TestAccount account = registerAndLogin();
        String refreshToken = account.refreshToken();

        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> refreshed = refresh(refreshToken);
            Assertions.assertEquals(200, refreshed.getStatusCode().value());
            refreshToken = body(refreshed).get("refreshToken").asString();
        }
    }

    // ---- revocation ----

    @Test
    void logout_ends_the_session_immediately() {
        TestAccount account = registerAndLogin();
        Assertions.assertEquals(200, status(HttpMethod.GET, "/accounts/me", account.token(), null));

        Assertions.assertEquals(204, status(HttpMethod.POST, "/accounts/logout", account.token(), null));

        Assertions.assertEquals(401, status(HttpMethod.GET, "/accounts/me", account.token(), null));
        Assertions.assertEquals(401, refresh(account.refreshToken()).getStatusCode().value());
    }

    @Test
    void logout_only_ends_the_current_session() {
        TestAccount phone = registerAndLogin();
        TestAccount computer = login(phone);

        Assertions.assertEquals(204, status(HttpMethod.POST, "/accounts/logout", phone.token(), null));

        Assertions.assertEquals(401, status(HttpMethod.GET, "/accounts/me", phone.token(), null));
        Assertions.assertEquals(200, status(HttpMethod.GET, "/accounts/me", computer.token(), null));
        Assertions.assertEquals(200, refresh(computer.refreshToken()).getStatusCode().value());
    }

    @Test
    void logout_all_ends_every_session_of_the_account_and_only_of_that_account() {
        TestAccount phone = registerAndLogin();
        TestAccount computer = login(phone);
        TestAccount other = registerAndLogin();

        Assertions.assertEquals(204, status(HttpMethod.POST, "/accounts/logout-all", phone.token(), null));

        Assertions.assertEquals(401, status(HttpMethod.GET, "/accounts/me", phone.token(), null));
        Assertions.assertEquals(401, status(HttpMethod.GET, "/accounts/me", computer.token(), null));
        Assertions.assertEquals(401, refresh(computer.refreshToken()).getStatusCode().value());
        Assertions.assertEquals(200, status(HttpMethod.GET, "/accounts/me", other.token(), null));
    }

    @Test
    void logout_requires_to_be_logged_in() {
        Assertions.assertEquals(401, status(HttpMethod.POST, "/accounts/logout", null, null));
        Assertions.assertEquals(401, status(HttpMethod.POST, "/accounts/logout-all", null, null));
    }

    @Test
    void changing_the_password_ends_every_session() {
        TestAccount account = registerAndLogin();
        TestAccount otherDevice = login(account);
        String newPseudo = account.pseudo() + "x";

        Assertions.assertEquals(200, status(HttpMethod.PATCH, "/accounts", account.token(),
                Map.of("currentPassword", account.password(), "email", newPseudo + "@test.fr",
                        "newPassword", "another-password", "pseudo", newPseudo)));

        Assertions.assertEquals(401, status(HttpMethod.GET, "/accounts/me", account.token(), null));
        Assertions.assertEquals(401, status(HttpMethod.GET, "/accounts/me", otherDevice.token(), null));
        Assertions.assertEquals(401, refresh(otherDevice.refreshToken()).getStatusCode().value());
        Assertions.assertEquals(401, status(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", newPseudo, "password", account.password())), "the old password no longer works");
        Assertions.assertEquals(200, status(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", newPseudo, "password", "another-password")));
    }

    @Test
    void deleting_the_account_ends_its_sessions() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(204, deleteAccount(account));

        Assertions.assertEquals(401, refresh(account.refreshToken()).getStatusCode().value());
    }

    @Test
    void a_token_without_session_is_refused_even_when_correctly_signed() {
        // an access token minted before sessions existed, or forged with a leaked key, has no valid sid
        TestAccount account = registerAndLogin();
        String withoutSid = jwtWithoutSession(account);

        Assertions.assertEquals(401, status(HttpMethod.GET, "/accounts/me", withoutSid, null));
    }

    // ---- brute force on the password ----

    @Test
    void the_account_is_locked_after_five_failed_logins_even_for_the_right_password() {
        TestAccount account = registerAndLogin();

        for (int i = 0; i < 5; i++) {
            Assertions.assertEquals(401, status(HttpMethod.POST, "/accounts/login", null,
                    Map.of("pseudo", account.pseudo(), "password", "wrong-" + i)));
        }

        ResponseEntity<String> locked = call(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", account.pseudo(), "password", account.password()));
        Assertions.assertEquals(429, locked.getStatusCode().value());
        long retryAfter = Long.parseLong(locked.getHeaders().getFirst("Retry-After"));
        Assertions.assertTrue(retryAfter > 800 && retryAfter <= 900, "locked for about 15 minutes but was " + retryAfter + "s");
    }

    @Test
    void a_lock_does_not_affect_other_accounts_nor_the_sessions_already_open() {
        TestAccount victim = registerAndLogin();
        TestAccount other = registerAndLogin();

        for (int i = 0; i < 5; i++) {
            call(HttpMethod.POST, "/accounts/login", null, Map.of("pseudo", victim.pseudo(), "password", "wrong-" + i));
        }

        Assertions.assertEquals(429, status(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", victim.pseudo(), "password", victim.password())));
        Assertions.assertEquals(200, status(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", other.pseudo(), "password", other.password())));
        Assertions.assertEquals(200, status(HttpMethod.GET, "/accounts/me", victim.token(), null));
    }

    @Test
    void a_successful_login_resets_the_counter_of_failures() {
        TestAccount account = registerAndLogin();

        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 4; i++) {
                Assertions.assertEquals(401, status(HttpMethod.POST, "/accounts/login", null,
                        Map.of("pseudo", account.pseudo(), "password", "wrong-" + i)));
            }
            Assertions.assertEquals(200, status(HttpMethod.POST, "/accounts/login", null,
                    Map.of("pseudo", account.pseudo(), "password", account.password())), "round " + round);
        }
    }

    @Test
    void failed_logins_are_counted_even_when_sent_in_parallel() throws Exception {
        TestAccount account = registerAndLogin();

        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(10)) {
            var attempts = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 10; i++) {
                int n = i;
                attempts.add(executor.submit(() -> status(HttpMethod.POST, "/accounts/login", null,
                        Map.of("pseudo", account.pseudo(), "password", "wrong-" + n))));
            }
            for (var attempt : attempts) {
                attempt.get();
            }
        }

        Assertions.assertEquals(429, status(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", account.pseudo(), "password", account.password())),
                "ten failures sent at once must lock the account");
    }

    @Test
    void an_unknown_pseudo_gets_the_same_answer_as_a_wrong_password() {
        TestAccount account = registerAndLogin();

        int wrongPassword = status(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", account.pseudo(), "password", "wrong-password"));
        int unknownPseudo = status(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", "nobody-" + account.pseudo(), "password", "wrong-password"));

        Assertions.assertEquals(wrongPassword, unknownPseudo);
    }

    // ---- helpers ----

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;

    private String jwtWithoutSession(TestAccount account) {
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .subject(account.id().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        return jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(
                        org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
