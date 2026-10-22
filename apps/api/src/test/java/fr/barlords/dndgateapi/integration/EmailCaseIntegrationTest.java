package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An e-mail address designates one account whatever its case (review v2, point 32): it is lower-cased when it
 * comes in, and the database refuses two addresses that differ only by case.
 */
class EmailCaseIntegrationTest extends ApiIntegrationTestSupport {

    private String newPseudo() {
        return "case" + UUID.randomUUID().toString().substring(0, 8);
    }

    private ResponseEntity<String> register(String pseudo, String email) {
        return call(HttpMethod.POST, "/accounts", null, Map.of("email", email, "password", "password123", "pseudo", pseudo));
    }

    private String verificationCode(String pseudo) {
        return jdbc.queryForObject("select verification_code from accounts where pseudo = ?", String.class, pseudo);
    }

    private String emailOf(String pseudo) {
        return jdbc.queryForObject("select email from accounts where pseudo = ?", String.class, pseudo);
    }

    private ResponseEntity<String> requestChange(TestAccount account, String newEmail) {
        Map<String, Object> body = new HashMap<>();
        body.put("currentPassword", account.password());
        body.put("email", newEmail);
        return call(HttpMethod.PATCH, "/accounts", account.token(), body);
    }

    @Test
    void an_address_is_stored_and_returned_in_lower_case() {
        String pseudo = newPseudo();

        ResponseEntity<String> response = register(pseudo, pseudo + "@Mail.COM");

        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals(pseudo + "@mail.com", body(response).get("email").asString());
        Assertions.assertEquals(pseudo + "@mail.com", emailOf(pseudo));
    }

    @Test
    void the_same_address_in_another_case_cannot_open_a_second_account() {
        String first = newPseudo();
        String second = newPseudo();
        Assertions.assertEquals(201, register(first, first + "@mail.com").getStatusCode().value());

        ResponseEntity<String> response = register(second, (first + "@Mail.Com").toUpperCase());

        Assertions.assertEquals(409, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from accounts where pseudo = ?", Integer.class, second));
    }

    @Test
    void the_address_can_be_retyped_in_another_case_to_verify_it() {
        String pseudo = newPseudo();
        register(pseudo, pseudo + "@mail.com");

        Assertions.assertEquals(204, verifyEmail((pseudo + "@Mail.com").toUpperCase(), verificationCode(pseudo)));

        Assertions.assertTrue(jdbc.queryForObject("select activated from accounts where pseudo = ?", Boolean.class, pseudo));
    }

    @Test
    void the_address_can_be_retyped_in_another_case_to_ask_for_a_new_code() {
        String pseudo = newPseudo();
        register(pseudo, pseudo + "@mail.com");
        String before = verificationCode(pseudo);
        // the mail just sent is old enough for a new one to be allowed
        jdbc.update("update accounts set verification_expires_at = now() where pseudo = ?", pseudo);

        ResponseEntity<String> response = call(HttpMethod.POST, "/accounts/verification-email", null,
                Map.of("email", (pseudo + "@mail.com").toUpperCase()));

        Assertions.assertEquals(202, response.getStatusCode().value());
        Assertions.assertNotEquals(before, verificationCode(pseudo), "a new code was issued");
    }

    @Test
    void a_new_address_is_kept_in_lower_case_while_it_waits_for_its_confirmation() {
        TestAccount account = registerAndLogin();
        String mixed = "New-" + account.pseudo() + "@Mail.COM";

        ResponseEntity<String> response = requestChange(account, mixed);

        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals(mixed.toLowerCase(), body(response).get("pendingEmail").asString());
        Assertions.assertEquals(mixed.toLowerCase(),
                jdbc.queryForObject("select pending_email from accounts where id = ?", String.class, account.id()));
    }

    @Test
    void the_new_address_can_be_confirmed_whatever_the_case_it_is_typed_in() {
        TestAccount account = registerAndLogin();
        String newEmail = "new-" + account.pseudo() + "@mail.com";
        requestChange(account, newEmail);
        String code = jdbc.queryForObject("select pending_email_code from accounts where id = ?", String.class, account.id());

        int status = call(HttpMethod.POST, "/accounts/email/confirm", null,
                Map.of("email", newEmail.toUpperCase(), "code", code)).getStatusCode().value();

        Assertions.assertEquals(204, status);
        Assertions.assertEquals(newEmail, jdbc.queryForObject("select email from accounts where id = ?", String.class, account.id()));
    }

    @Test
    void the_address_of_another_account_in_another_case_cannot_be_asked_for() {
        TestAccount owner = registerAndLogin();
        TestAccount other = registerAndLogin();

        ResponseEntity<String> response = requestChange(other, (owner.pseudo() + "@Test.fr").toUpperCase());

        Assertions.assertEquals(409, response.getStatusCode().value(), response.getBody());
    }

    @Test
    void the_same_address_with_another_case_is_not_a_change() {
        TestAccount account = registerAndLogin();

        ResponseEntity<String> response = requestChange(account, (account.pseudo() + "@Test.fr").toUpperCase());

        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
        Assertions.assertTrue(body(response).path("pendingEmail").isNull() || body(response).path("pendingEmail").isMissingNode(), "nothing to confirm: it is the same address");
    }
}
