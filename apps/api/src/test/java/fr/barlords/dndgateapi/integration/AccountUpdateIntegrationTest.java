package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Changing an account needs the current password: an access token alone, which can be stolen,
 * must not be enough to take it over.
 */
class AccountUpdateIntegrationTest extends ApiIntegrationTestSupport {

    private ResponseEntity<String> update(TestAccount account, Map<String, Object> body) {
        return call(HttpMethod.PATCH, "/accounts", account.token(), body);
    }

    private Map<String, Object> changes(TestAccount account, String field, String value) {
        Map<String, Object> body = new HashMap<>();
        body.put("currentPassword", account.password());
        body.put(field, value);
        return body;
    }

    private int login(String pseudo, String password) {
        return call(HttpMethod.POST, "/accounts/login", null, Map.of("pseudo", pseudo, "password", password))
                .getStatusCode().value();
    }

    private int me(String token) {
        return call(HttpMethod.GET, "/accounts/me", token, null).getStatusCode().value();
    }

    private String pseudoInDatabase(TestAccount account) {
        return jdbc.queryForObject("select pseudo from accounts where id = ?", String.class, account.id());
    }

    // ---- the current password is required ----

    @Test
    void the_current_password_is_mandatory() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(400, update(account, Map.of("newPassword", "another-password")).getStatusCode().value());
        Assertions.assertEquals(400, update(account, Map.of("currentPassword", "", "newPassword", "another-password")).getStatusCode().value());

        Assertions.assertEquals(200, login(account.pseudo(), account.password()), "the password was not changed");
    }

    @Test
    void a_stolen_access_token_cannot_change_the_password() {
        TestAccount account = registerAndLogin();
        Map<String, Object> attack = new HashMap<>();
        attack.put("currentPassword", "what-the-thief-guessed");
        attack.put("newPassword", "thief-password");
        attack.put("email", "thief@test.fr");

        Assertions.assertEquals(403, update(account, attack).getStatusCode().value());

        Assertions.assertEquals(200, login(account.pseudo(), account.password()), "the real password still works");
        Assertions.assertEquals(401, login(account.pseudo(), "thief-password"));
        Assertions.assertEquals(200, me(account.token()), "403 and not 401: the session stays open");
        Assertions.assertEquals(account.pseudo() + "@test.fr",
                jdbc.queryForObject("select email from accounts where id = ?", String.class, account.id()));
    }

    @Test
    void a_wrong_current_password_changes_nothing_at_all() {
        TestAccount account = registerAndLogin();
        Map<String, Object> body = new HashMap<>();
        body.put("currentPassword", "wrong-password");
        body.put("pseudo", "renamed" + account.pseudo());

        Assertions.assertEquals(403, update(account, body).getStatusCode().value());

        Assertions.assertEquals(account.pseudo(), pseudoInDatabase(account));
    }

    @Test
    void wrong_current_passwords_count_toward_the_lockout() {
        TestAccount account = registerAndLogin();

        for (int i = 0; i < 5; i++) {
            Map<String, Object> body = new HashMap<>();
            body.put("currentPassword", "guess-" + i);
            body.put("newPassword", "thief-password");
            Assertions.assertEquals(403, update(account, body).getStatusCode().value());
        }

        ResponseEntity<String> locked = update(account, changes(account, "newPassword", "another-password"));
        Assertions.assertEquals(429, locked.getStatusCode().value(), "the right password is refused too while locked");
        Assertions.assertEquals(429, login(account.pseudo(), account.password()));
        Assertions.assertEquals(429, login(account.pseudo(), "another-password"), "while locked, even a wrong password is not examined");
    }

    // ---- what can be changed ----

    @Test
    void only_the_given_fields_change_and_the_sessions_stay_open() {
        TestAccount account = registerAndLogin();
        TestAccount otherDevice = login(account);
        String newPseudo = "renamed" + account.pseudo();

        ResponseEntity<String> response = update(account, changes(account, "pseudo", newPseudo));

        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals(newPseudo, body(response).get("pseudo").asString());
        Assertions.assertEquals(account.pseudo() + "@test.fr", body(response).get("email").asString());
        Assertions.assertEquals(200, login(newPseudo, account.password()), "the password is unchanged");
        Assertions.assertEquals(200, me(account.token()));
        Assertions.assertEquals(200, me(otherDevice.token()));
    }

    @Test
    void the_email_can_be_asked_for_alone_and_waits_for_its_confirmation() {
        TestAccount account = registerAndLogin();

        ResponseEntity<String> response = update(account, changes(account, "email", "new-" + account.pseudo() + "@test.fr"));

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertEquals(account.pseudo() + "@test.fr",
                jdbc.queryForObject("select email from accounts where id = ?", String.class, account.id()));
        Assertions.assertEquals("new-" + account.pseudo() + "@test.fr",
                jdbc.queryForObject("select pending_email from accounts where id = ?", String.class, account.id()));
    }

    @Test
    void the_password_can_be_changed_alone_which_ends_every_session() {
        TestAccount account = registerAndLogin();
        TestAccount otherDevice = login(account);

        // used to need a new pseudo and a new e-mail too, since the old ones counted as "already used"
        Assertions.assertEquals(200, update(account, changes(account, "newPassword", "another-password")).getStatusCode().value());

        Assertions.assertEquals(401, me(account.token()));
        Assertions.assertEquals(401, me(otherDevice.token()));
        Assertions.assertEquals(401, login(account.pseudo(), account.password()));
        Assertions.assertEquals(200, login(account.pseudo(), "another-password"));
    }

    @Test
    void sending_the_same_values_again_is_not_a_conflict_with_oneself() {
        TestAccount account = registerAndLogin();
        Map<String, Object> body = new HashMap<>();
        body.put("currentPassword", account.password());
        body.put("pseudo", account.pseudo());
        body.put("email", account.pseudo() + "@test.fr");
        body.put("newPassword", account.password());

        Assertions.assertEquals(200, update(account, body).getStatusCode().value());
        Assertions.assertEquals(200, me(account.token()), "nothing changed, nobody is logged out");
    }

    @Test
    void a_pseudo_or_an_email_used_by_someone_else_is_refused() {
        TestAccount account = registerAndLogin();
        TestAccount other = registerAndLogin();

        Assertions.assertEquals(409, update(account, changes(account, "pseudo", other.pseudo())).getStatusCode().value());
        Assertions.assertEquals(409, update(account, changes(account, "email", other.pseudo() + "@test.fr")).getStatusCode().value());
        Assertions.assertEquals(account.pseudo(), pseudoInDatabase(account));
    }

    @Test
    void the_new_password_follows_the_password_policy() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(400, update(account, changes(account, "newPassword", "short")).getStatusCode().value());
        Assertions.assertEquals(400, update(account, changes(account, "newPassword", "a".repeat(73))).getStatusCode().value());
        Assertions.assertEquals(200, login(account.pseudo(), account.password()));
    }

    @Test
    void asking_for_no_change_is_accepted_and_changes_nothing() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(200, update(account, Map.of("currentPassword", account.password())).getStatusCode().value());
        Assertions.assertEquals(200, me(account.token()));
    }
}
