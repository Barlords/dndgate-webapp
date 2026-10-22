package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.functional.exception.account.PseudoAlreadyUsedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The account is created when the owner of the address confirms the signup (review v2, point 59): from the signup to
 * the login, what happens when the pseudo or the address is taken in the meantime, and simultaneous confirmations.
 */
class SignupConfirmationIntegrationTest extends ApiIntegrationTestSupport {

    private String newPseudo() {
        return "confirm" + UUID.randomUUID().toString().substring(0, 8);
    }

    private ResponseEntity<String> register(String pseudo, String email) {
        return call(HttpMethod.POST, "/accounts", null, Map.of("email", email, "password", "password123", "pseudo", pseudo));
    }

    private int count(String sql, Object... parameters) {
        return jdbc.queryForObject(sql, Integer.class, parameters);
    }

    private ResponseEntity<String> verify(String email, String code) {
        return call(HttpMethod.POST, "/accounts/verify", null, Map.of("email", email, "code", code));
    }

    private List<Integer> inParallel(List<Callable<Integer>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(2, calls.size()));
        try {
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> status : pool.invokeAll(calls)) {
                statuses.add(status.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void from_the_signup_to_the_login_through_the_code_of_the_mail() {
        String pseudo = newPseudo();
        String email = pseudo + "@test.fr";

        Assertions.assertEquals(202, register(pseudo, email).getStatusCode().value());
        String code = lastSignupCode(email);
        Assertions.assertEquals(204, verify(email, code).getStatusCode().value());

        ResponseEntity<String> login = call(HttpMethod.POST, "/accounts/login", null, Map.of("pseudo", pseudo, "password", "password123"));
        Assertions.assertEquals(200, login.getStatusCode().value(), login.getBody());
        String token = body(login).get("token").asString();
        ResponseEntity<String> me = call(HttpMethod.GET, "/accounts/me", token, null);
        Assertions.assertEquals(200, me.getStatusCode().value());
        Assertions.assertEquals(pseudo, body(me).get("pseudo").asString());
    }

    @Test
    void the_account_keeps_the_pseudo_and_the_password_of_the_signup() {
        String pseudo = newPseudo();
        String email = pseudo + "@test.fr";
        register(pseudo, email);
        String hashOfTheSignup = jdbc.queryForObject("select password_hash from pending_signups where email = ?", String.class, email);

        Assertions.assertEquals(204, verify(email, lastSignupCode(email)).getStatusCode().value());

        Map<String, Object> account = jdbc.queryForMap("select pseudo, email, password, activated from accounts where email = ?", email);
        Assertions.assertEquals(pseudo, account.get("pseudo"));
        Assertions.assertEquals(hashOfTheSignup, account.get("password"));
        Assertions.assertEquals(true, account.get("activated"));
        Assertions.assertEquals(0, count("select count(*) from pending_signups where email = ?", email));
    }

    @Test
    void a_pseudo_taken_in_the_meantime_is_a_conflict_and_the_signup_is_forgotten() {
        String pseudo = newPseudo();
        String email = pseudo + "@test.fr";
        register(pseudo, email);
        String code = lastSignupCode(email);
        // somebody else completed a signup with the same pseudo before the link was followed
        createAccount(pseudo, "other-" + email, "password123");

        ResponseEntity<String> response = verify(email, code);

        Assertions.assertEquals(409, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals(PseudoAlreadyUsedException.EXCEPTION_MESSAGE, body(response).get("detail").asString());
        Assertions.assertEquals(0, count("select count(*) from accounts where email = ?", email));
        Assertions.assertEquals(0, count("select count(*) from pending_signups where email = ?", email), "the signup is forgotten");
        Assertions.assertEquals(400, verify(email, code).getStatusCode().value(), "and the code is dead");
    }

    @Test
    void an_address_that_got_an_account_in_the_meantime_is_an_invalid_code_and_the_signup_is_forgotten() {
        String pseudo = newPseudo();
        String email = pseudo + "@test.fr";
        register(pseudo, email);
        String code = lastSignupCode(email);
        // another account switched to this address (nothing reserves an address before it is confirmed)
        TestAccount other = registerAndLogin();
        Assertions.assertEquals(200, call(HttpMethod.PATCH, "/accounts", other.token(),
                Map.of("currentPassword", other.password(), "email", email)).getStatusCode().value());
        String changeCode = jdbc.queryForObject("select pending_email_code from accounts where id = ?", String.class, other.id());
        Assertions.assertEquals(204, call(HttpMethod.POST, "/accounts/email/confirm", null,
                Map.of("email", email, "code", changeCode)).getStatusCode().value());

        ResponseEntity<String> response = verify(email, code);

        ResponseEntity<String> wrongCode = verify(email, "wrong");
        Assertions.assertEquals(400, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals(withoutRequestId(wrongCode.getBody()), withoutRequestId(response.getBody()),
                "the answer is the one of an invalid code");
        Assertions.assertEquals(1, count("select count(*) from accounts where email = ?", email));
        Assertions.assertEquals(0, count("select count(*) from accounts where pseudo = ?", pseudo));
        Assertions.assertEquals(0, count("select count(*) from pending_signups where email = ?", email), "the signup is forgotten");
    }

    private static String withoutRequestId(String problem) {
        return problem.replaceAll(",\\s*\"correlationId\"\\s*:\\s*\"[^\"]*\"", "");
    }

    @Test
    void simultaneous_confirmations_with_the_same_code_create_exactly_one_account() throws Exception {
        for (int round = 0; round < 3; round++) {
            String pseudo = newPseudo();
            String email = pseudo + "@test.fr";
            register(pseudo, email);
            String code = lastSignupCode(email);

            List<Callable<Integer>> confirmations = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                confirmations.add(() -> verify(email, code).getStatusCode().value());
            }
            List<Integer> statuses = inParallel(confirmations);

            Assertions.assertEquals(1, statuses.stream().filter(status -> status == 204).count(), statuses.toString());
            Assertions.assertEquals(7, statuses.stream().filter(status -> status == 400).count(), "the others see an invalid code: " + statuses);
            Assertions.assertEquals(1, count("select count(*) from accounts where email = ?", email));
            Assertions.assertEquals(0, count("select count(*) from pending_signups where email = ?", email));
        }
    }

    @Test
    void two_signups_with_the_same_pseudo_confirmed_at_once_give_one_account_and_one_conflict() throws Exception {
        for (int round = 0; round < 5; round++) {
            String pseudo = newPseudo();
            String firstEmail = pseudo + "-1@test.fr";
            String secondEmail = pseudo + "-2@test.fr";
            register(pseudo, firstEmail);
            register(pseudo, secondEmail);
            String firstCode = lastSignupCode(firstEmail);
            String secondCode = lastSignupCode(secondEmail);

            List<Integer> statuses = inParallel(List.of(
                    () -> verify(firstEmail, firstCode).getStatusCode().value(),
                    () -> verify(secondEmail, secondCode).getStatusCode().value()));

            Assertions.assertEquals(List.of(204, 409), statuses.stream().sorted().toList(), "never a 500: " + statuses);
            Assertions.assertEquals(1, count("select count(*) from accounts where pseudo = ?", pseudo));
            Assertions.assertEquals(0, count("select count(*) from pending_signups where pseudo = ?", pseudo), "both signups are gone");
        }
    }
}
