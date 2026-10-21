package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

class PasswordPolicyIntegrationTest extends ApiIntegrationTestSupport {

    private String newPseudo() {
        return "pw" + UUID.randomUUID().toString().substring(0, 8);
    }

    private int register(String pseudo, String password) {
        return call(HttpMethod.POST, "/accounts", null,
                Map.of("email", pseudo + "@test.fr", "password", password, "pseudo", pseudo)).getStatusCode().value();
    }

    private void activate(String pseudo) {
        String code = jdbc.queryForObject("select verification_code from accounts where pseudo = ?", String.class, pseudo);
        Assertions.assertEquals(204, verifyEmail(pseudo + "@test.fr", code));
    }

    private int login(String pseudo, String password) {
        return call(HttpMethod.POST, "/accounts/login", null, Map.of("pseudo", pseudo, "password", password))
                .getStatusCode().value();
    }

    // ---- policy at registration ----

    @Test
    void a_password_shorter_than_8_characters_is_refused() {
        Assertions.assertEquals(400, register(newPseudo(), "abcdefg"));
        Assertions.assertEquals(201, register(newPseudo(), "abcdefgh"));
    }

    @Test
    void a_password_of_only_spaces_is_refused() {
        Assertions.assertEquals(400, register(newPseudo(), "        "));
    }

    @Test
    void a_password_longer_than_72_bytes_is_refused_instead_of_being_silently_truncated() {
        Assertions.assertEquals(201, register(newPseudo(), "a".repeat(72)));
        Assertions.assertEquals(400, register(newPseudo(), "a".repeat(73)));
        Assertions.assertEquals(201, register(newPseudo(), "€".repeat(24)));
        Assertions.assertEquals(400, register(newPseudo(), "€".repeat(25)));
    }

    @Test
    void the_policy_also_applies_when_the_password_is_changed() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(400, call(HttpMethod.PATCH, "/accounts", account.token(),
                Map.of("currentPassword", account.password(), "newPassword", "short"))
                .getStatusCode().value());
    }

    // ---- the password is stored and compared as typed ----

    @Test
    void spaces_around_the_password_are_kept() {
        String pseudo = newPseudo();
        register(pseudo, " secret123 ");
        activate(pseudo);

        Assertions.assertEquals(200, login(pseudo, " secret123 "), "the password as typed must work");
        Assertions.assertEquals(404, login(pseudo, "secret123"), "a different password must not");
    }

    @Test
    void a_password_with_accents_and_symbols_works() {
        String pseudo = newPseudo();
        register(pseudo, "Mot de passe très sûr ! €");
        activate(pseudo);

        Assertions.assertEquals(200, login(pseudo, "Mot de passe très sûr ! €"));
    }

    @Test
    void the_pseudo_is_trimmed_the_same_way_at_registration_and_login() {
        String pseudo = newPseudo();
        Assertions.assertEquals(201, call(HttpMethod.POST, "/accounts", null,
                Map.of("email", pseudo + "@test.fr", "password", "password123", "pseudo", " " + pseudo + " ")).getStatusCode().value());
        activate(pseudo);

        Assertions.assertEquals(200, login(pseudo, "password123"));
        Assertions.assertEquals(200, login("  " + pseudo + "  ", "password123"));
    }

    // ---- login input ----

    @Test
    void a_very_long_password_at_login_is_a_plain_refusal_not_a_server_error() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(404, login(account.pseudo(), "a".repeat(100)));
        Assertions.assertEquals(404, login(account.pseudo(), "€".repeat(60)));
    }

    @Test
    void an_oversized_or_empty_login_is_a_bad_request() {
        Assertions.assertEquals(400, login("someone", "a".repeat(5000)));
        Assertions.assertEquals(400, login("", "password123"));
        Assertions.assertEquals(400, login("someone", ""));
        Assertions.assertEquals(400, login("a".repeat(500), "password123"));
    }
}
