package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Changing the e-mail address is a request, not an order: the address in use only changes once the owner of the
 * new address followed the link sent to it. Driven over HTTP like the web page behind that link.
 */
class EmailChangeIntegrationTest extends ApiIntegrationTestSupport {

    private String newEmailFor(TestAccount account) {
        return "new-" + account.pseudo() + "@test.fr";
    }

    private ResponseEntity<String> requestChange(TestAccount account, String newEmail) {
        Map<String, Object> body = new HashMap<>();
        body.put("currentPassword", account.password());
        body.put("email", newEmail);
        return call(HttpMethod.PATCH, "/accounts", account.token(), body);
    }

    /** The code of the last "confirm your new address" mail that was sent. */
    private String codeOfTheLastMail() {
        ArgumentCaptor<Account> mails = ArgumentCaptor.forClass(Account.class);
        Mockito.verify(emailingSpi, Mockito.atLeastOnce()).sendEmailChangeEmail(mails.capture());
        return mails.getValue().getPendingEmailCode();
    }

    /** What the page behind the link of the mail does: no token, the link may be opened on another device. */
    private int confirm(String email, String code) {
        return call(HttpMethod.POST, "/accounts/email/confirm", null, Map.of("email", email, "code", code))
                .getStatusCode().value();
    }

    private String emailInDatabase(TestAccount account) {
        return jdbc.queryForObject("select email from accounts where id = ?", String.class, account.id());
    }

    private int loginStatus(TestAccount account) {
        return call(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", account.pseudo(), "password", account.password())).getStatusCode().value();
    }

    // ================= the nominal path =================

    @Test
    void the_old_address_stays_in_use_until_the_new_one_is_confirmed() {
        TestAccount account = registerAndLogin();
        String newEmail = newEmailFor(account);

        ResponseEntity<String> response = requestChange(account, newEmail);

        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals(account.pseudo() + "@test.fr", body(response).get("email").asString());
        Assertions.assertEquals(newEmail, body(response).get("pendingEmail").asString());
        Assertions.assertEquals(account.pseudo() + "@test.fr", emailInDatabase(account));
        Assertions.assertEquals(200, loginStatus(account));
    }

    @Test
    void the_code_is_not_given_back_in_the_response() {
        TestAccount account = registerAndLogin();

        ResponseEntity<String> response = requestChange(account, newEmailFor(account));

        Assertions.assertFalse(response.getBody().contains(codeOfTheLastMail()));
    }

    @Test
    void the_code_received_at_the_new_address_makes_it_the_address_of_the_account() {
        TestAccount account = registerAndLogin();
        String newEmail = newEmailFor(account);
        requestChange(account, newEmail);

        Assertions.assertEquals(204, confirm(newEmail, codeOfTheLastMail()));

        Assertions.assertEquals(newEmail, emailInDatabase(account));
        Assertions.assertNull(jdbc.queryForObject("select pending_email from accounts where id = ?", String.class, account.id()));
        Assertions.assertNull(jdbc.queryForObject("select pending_email_code from accounts where id = ?", String.class, account.id()));
        Assertions.assertEquals(200, loginStatus(account));
        Assertions.assertTrue(body(call(HttpMethod.GET, "/accounts/me", account.token(), null)).get("pendingEmail").isNull());
    }

    @Test
    void an_address_with_a_plus_sign_can_be_confirmed() {
        TestAccount account = registerAndLogin();
        String newEmail = "new+rpg-" + account.pseudo() + "@test.fr";
        requestChange(account, newEmail);

        Assertions.assertEquals(204, confirm(newEmail, codeOfTheLastMail()));

        Assertions.assertEquals(newEmail, emailInDatabase(account));
    }

    // ================= what is refused =================

    @Test
    void a_wrong_code_changes_nothing() {
        TestAccount account = registerAndLogin();
        String newEmail = newEmailFor(account);
        requestChange(account, newEmail);

        Assertions.assertEquals(400, confirm(newEmail, "not-the-code"));

        Assertions.assertEquals(account.pseudo() + "@test.fr", emailInDatabase(account));
    }

    @Test
    void the_registration_code_cannot_confirm_an_address_change() {
        TestAccount account = registerAndLogin();
        String newEmail = newEmailFor(account);
        requestChange(account, newEmail);
        // somebody signs up with that same address: the code of that pending signup is not the one of the change
        String signupPseudo = "other" + UUID.randomUUID().toString().substring(0, 8);
        Assertions.assertEquals(202, call(HttpMethod.POST, "/accounts", null,
                Map.of("email", newEmail, "password", "password123", "pseudo", signupPseudo)).getStatusCode().value());
        String registrationCode = lastSignupCode(newEmail);

        Assertions.assertEquals(400, confirm(newEmail, registrationCode));
        Assertions.assertEquals(400, confirm(account.pseudo() + "@test.fr", registrationCode));
    }

    @Test
    void an_expired_code_changes_nothing() {
        TestAccount account = registerAndLogin();
        String newEmail = newEmailFor(account);
        requestChange(account, newEmail);
        String code = codeOfTheLastMail();
        jdbc.update("update accounts set pending_email_expires_at = now() - interval '1 second' where id = ?", account.id());

        Assertions.assertEquals(400, confirm(newEmail, code));

        Assertions.assertEquals(account.pseudo() + "@test.fr", emailInDatabase(account));
    }

    @Test
    void a_code_works_once() {
        TestAccount account = registerAndLogin();
        String newEmail = newEmailFor(account);
        requestChange(account, newEmail);
        String code = codeOfTheLastMail();
        Assertions.assertEquals(204, confirm(newEmail, code));

        Assertions.assertEquals(400, confirm(newEmail, code));
    }

    @Test
    void an_address_nobody_asked_for_gets_the_same_answer_as_a_wrong_code() {
        Assertions.assertEquals(400, confirm("nobody-" + UUID.randomUUID() + "@test.fr", "whatever"));
    }

    @Test
    void asking_again_cancels_the_previous_request() {
        TestAccount account = registerAndLogin();
        String first = "first-" + account.pseudo() + "@test.fr";
        String second = "second-" + account.pseudo() + "@test.fr";
        requestChange(account, first);
        String firstCode = codeOfTheLastMail();
        requestChange(account, second);

        Assertions.assertEquals(400, confirm(first, firstCode), "the first link is dead");
        Assertions.assertEquals(204, confirm(second, codeOfTheLastMail()));
        Assertions.assertEquals(second, emailInDatabase(account));
    }

    @Test
    void an_address_already_used_cannot_be_asked_for() {
        TestAccount account = registerAndLogin();
        TestAccount other = registerAndLogin();

        Assertions.assertEquals(409, requestChange(account, other.pseudo() + "@test.fr").getStatusCode().value());

        Assertions.assertNull(jdbc.queryForObject("select pending_email from accounts where id = ?", String.class, account.id()));
    }

    @Test
    void an_address_taken_while_the_link_waited_is_refused() {
        TestAccount account = registerAndLogin();
        String newEmail = newEmailFor(account);
        requestChange(account, newEmail);
        String code = codeOfTheLastMail();
        TestAccount other = registerAndLogin();
        jdbc.update("update accounts set email = ? where id = ?", newEmail, other.id());

        Assertions.assertEquals(409, confirm(newEmail, code));

        Assertions.assertEquals(account.pseudo() + "@test.fr", emailInDatabase(account));
    }

    @Test
    void the_change_needs_the_current_password_like_before() {
        TestAccount account = registerAndLogin();
        Map<String, Object> body = new HashMap<>();
        body.put("currentPassword", "wrong-password");
        body.put("email", newEmailFor(account));

        Assertions.assertEquals(403, call(HttpMethod.PATCH, "/accounts", account.token(), body).getStatusCode().value());

        Assertions.assertNull(jdbc.queryForObject("select pending_email from accounts where id = ?", String.class, account.id()));
        Mockito.verify(emailingSpi, Mockito.never()).sendEmailChangeEmail(Mockito.argThat(
                sent -> newEmailFor(account).equals(sent.getPendingEmail())));
    }

    @Test
    void the_other_fields_of_the_request_apply_at_once() {
        TestAccount account = registerAndLogin();
        Map<String, Object> body = new HashMap<>();
        body.put("currentPassword", account.password());
        body.put("email", newEmailFor(account));
        body.put("pseudo", "renamed" + account.pseudo());

        ResponseEntity<String> response = call(HttpMethod.PATCH, "/accounts", account.token(), body);

        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals("renamed" + account.pseudo(), body(response).get("pseudo").asString());
        Assertions.assertEquals(account.pseudo() + "@test.fr", body(response).get("email").asString());
    }
}
