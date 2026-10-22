package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.functional.exception.EmailNotSendException;
import fr.barlords.dndgateapi.domain.functional.model.account.PendingSignup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

/**
 * The confirmation of a pending signup and the request for a new mail, driven over HTTP like the web page behind
 * the link of the mail. The mail server is replaced by a mock which keeps what was sent.
 */
class EmailVerificationIntegrationTest extends ApiIntegrationTestSupport {

    private String newPseudo() {
        return "mail" + UUID.randomUUID().toString().substring(0, 8);
    }

    private ResponseEntity<String> register(String pseudo, String email) {
        return call(HttpMethod.POST, "/accounts", null, Map.of("email", email, "password", "password123", "pseudo", pseudo));
    }

    private int resend(String email) {
        return call(HttpMethod.POST, "/accounts/verification-email", null, Map.of("email", email)).getStatusCode().value();
    }

    private int login(String pseudo) {
        return call(HttpMethod.POST, "/accounts/login", null, Map.of("pseudo", pseudo, "password", "password123"))
                .getStatusCode().value();
    }

    private boolean accountExists(String pseudo) {
        return jdbc.queryForObject("select count(*) from accounts where pseudo = ?", Integer.class, pseudo) == 1;
    }

    private int pendingSignups(String email) {
        return jdbc.queryForObject("select count(*) from pending_signups where email = ?", Integer.class, email);
    }

    /** A pending signup, and the clear code of its mail. */
    private String signUp(String pseudo, String email) {
        Assertions.assertEquals(202, register(pseudo, email).getStatusCode().value());
        return lastSignupCode(email);
    }

    private void pretendTheLastMailWasSent(String email, String ago) {
        jdbc.update("update pending_signups set created_at = now() - interval '" + ago + "' where email = ?", email);
    }

    // ================= the nominal path =================

    @Test
    void the_code_received_by_mail_creates_the_account_and_it_can_log_in() {
        String pseudo = newPseudo();
        String email = pseudo + "@test.fr";
        String code = signUp(pseudo, email);
        Assertions.assertEquals(401, login(pseudo), "not usable before the address is verified");
        Assertions.assertFalse(accountExists(pseudo), "no account exists before the link was followed");

        Assertions.assertEquals(204, verifyEmail(email, code));

        Assertions.assertTrue(accountExists(pseudo));
        Assertions.assertTrue(jdbc.queryForObject("select activated from accounts where pseudo = ?", Boolean.class, pseudo));
        Assertions.assertEquals(0, pendingSignups(email), "the signup is used up");
        Assertions.assertEquals(200, login(pseudo));
    }

    @Test
    void the_code_is_not_given_back_in_the_response_to_the_registration() {
        String pseudo = newPseudo();

        ResponseEntity<String> response = register(pseudo, pseudo + "@test.fr");

        Assertions.assertEquals(1, body(response).size(), "the answer carries a message only: no id, no code");
        Assertions.assertFalse(response.getBody().contains(pseudo));
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        Mockito.verify(emailingSpi).sendSignupConfirmationEmail(any(), code.capture());
        Assertions.assertFalse(code.getValue().isBlank());
        Assertions.assertFalse(response.getBody().contains(code.getValue()));
    }

    @Test
    void an_address_with_a_plus_sign_can_be_verified() {
        String pseudo = newPseudo();
        String email = "name+rpg" + pseudo + "@test.fr";
        String code = signUp(pseudo, email);

        Assertions.assertEquals(204, verifyEmail(email, code));
        Assertions.assertEquals(200, login(pseudo));
    }

    // ================= what does not work =================

    @Test
    void a_code_can_only_be_used_once() {
        String pseudo = newPseudo();
        String email = pseudo + "@test.fr";
        String code = signUp(pseudo, email);

        Assertions.assertEquals(204, verifyEmail(email, code));
        Assertions.assertEquals(400, verifyEmail(email, code));
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from accounts where email = ?", Integer.class, email));
    }

    @Test
    void a_wrong_code_creates_nothing_and_keeps_the_signup_waiting() {
        String pseudo = newPseudo();
        String email = pseudo + "@test.fr";
        String code = signUp(pseudo, email);

        Assertions.assertEquals(400, verifyEmail(email, "wrong" + code));
        Assertions.assertEquals(400, verifyEmail(email, code.toUpperCase().equals(code) ? code.toLowerCase() : code.toUpperCase()));
        Assertions.assertEquals(400, verifyEmail(email, code.substring(1)));
        Assertions.assertFalse(accountExists(pseudo));
        Assertions.assertEquals(1, pendingSignups(email), "somebody guessing cannot destroy the signup of the owner");
        Assertions.assertEquals(204, verifyEmail(email, code), "the right code still works");
    }

    @Test
    void a_code_given_for_another_address_does_not_work() {
        String first = newPseudo();
        String second = newPseudo();
        String codeOfFirst = signUp(first, first + "@test.fr");
        signUp(second, second + "@test.fr");

        Assertions.assertEquals(400, verifyEmail(second + "@test.fr", codeOfFirst));
        Assertions.assertFalse(accountExists(second));
    }

    @Test
    void an_unknown_address_gets_the_same_answer_as_a_wrong_code() {
        String pseudo = newPseudo();
        signUp(pseudo, pseudo + "@test.fr");

        ResponseEntity<String> wrongCode = call(HttpMethod.POST, "/accounts/verify", null,
                Map.of("email", pseudo + "@test.fr", "code", "wrong"));
        ResponseEntity<String> unknownAddress = call(HttpMethod.POST, "/accounts/verify", null,
                Map.of("email", "nobody" + pseudo + "@test.fr", "code", "wrong"));

        Assertions.assertEquals(400, wrongCode.getStatusCode().value());
        Assertions.assertEquals(wrongCode.getStatusCode(), unknownAddress.getStatusCode());
        // the id of the request differs from one call to the next: everything else must be the same
        Assertions.assertEquals(withoutRequestId(wrongCode.getBody()), withoutRequestId(unknownAddress.getBody()),
                "nothing tells which addresses are registered");
    }

    private static String withoutRequestId(String problem) {
        return problem.replaceAll(",\\s*\"correlationId\"\\s*:\\s*\"[^\"]*\"", "");
    }

    @Test
    void a_code_that_has_expired_does_not_work() {
        String pseudo = newPseudo();
        String email = pseudo + "@test.fr";
        String code = signUp(pseudo, email);
        jdbc.update("update pending_signups set expires_at = now() - interval '1 minute' where email = ?", email);

        Assertions.assertEquals(400, verifyEmail(email, code));
        Assertions.assertFalse(accountExists(pseudo));
    }

    @Test
    void the_old_link_that_changed_something_with_a_get_is_gone() {
        String pseudo = newPseudo();
        String email = pseudo + "@test.fr";
        String code = signUp(pseudo, email);

        ResponseEntity<String> response = call(HttpMethod.GET, "/accounts/verify?email=" + email + "&code=" + code, null, null);

        Assertions.assertFalse(response.getStatusCode().is2xxSuccessful());
        Assertions.assertFalse(accountExists(pseudo), "following a link must not change anything");
    }

    @Test
    void a_request_without_address_or_code_is_a_bad_request() {
        Assertions.assertEquals(400, call(HttpMethod.POST, "/accounts/verify", null, Map.of("email", "a@test.fr")).getStatusCode().value());
        Assertions.assertEquals(400, call(HttpMethod.POST, "/accounts/verify", null, Map.of("code", "abc")).getStatusCode().value());
        Assertions.assertEquals(400, call(HttpMethod.POST, "/accounts/verify", null, Map.of("email", "", "code", "")).getStatusCode().value());
    }

    // ================= a mail that did not arrive =================

    @Test
    void a_new_mail_can_be_asked_for_and_replaces_the_old_code() {
        String pseudo = newPseudo();
        String email = pseudo + "@test.fr";
        String oldCode = signUp(pseudo, email);
        pretendTheLastMailWasSent(email, "2 minutes");

        Assertions.assertEquals(202, resend(email));

        String newCode = lastSignupCode(email);
        Assertions.assertNotEquals(oldCode, newCode);
        Assertions.assertEquals(400, verifyEmail(email, oldCode), "the old code is dead");
        Assertions.assertEquals(204, verifyEmail(email, newCode));
        Assertions.assertEquals(200, login(pseudo));
    }

    @Test
    void the_new_mail_comes_with_a_new_expiry() {
        String pseudo = newPseudo();
        String email = pseudo + "@test.fr";
        signUp(pseudo, email);
        jdbc.update("update pending_signups set created_at = now() - interval '3 days', expires_at = now() - interval '2 days' where email = ?", email);

        Assertions.assertEquals(202, resend(email));

        Assertions.assertEquals(204, verifyEmail(email, lastSignupCode(email)), "an expired signup is renewed the same way");
    }

    @Test
    void only_the_hash_of_the_new_code_is_kept() {
        String pseudo = newPseudo();
        String email = pseudo + "@test.fr";
        signUp(pseudo, email);
        pretendTheLastMailWasSent(email, "2 minutes");

        resend(email);

        Assertions.assertEquals(PendingSignup.hashOf(lastSignupCode(email)),
                jdbc.queryForObject("select code_hash from pending_signups where email = ?", String.class, email));
    }

    @Test
    void a_second_request_right_after_the_first_sends_nothing() {
        String pseudo = newPseudo();
        String email = pseudo + "@test.fr";
        signUp(pseudo, email);
        pretendTheLastMailWasSent(email, "2 minutes");

        Assertions.assertEquals(202, resend(email));
        Assertions.assertEquals(202, resend(email));

        Mockito.verify(emailingSpi, Mockito.times(2)).sendSignupConfirmationEmail(any(), any());
        Assertions.assertEquals(204, verifyEmail(email, lastSignupCode(email)));
    }

    @Test
    void a_request_inside_the_cooldown_of_the_signup_itself_sends_nothing_and_keeps_the_code() {
        String pseudo = newPseudo();
        String email = pseudo + "@test.fr";
        String code = signUp(pseudo, email);

        Assertions.assertEquals(202, resend(email));

        Mockito.verify(emailingSpi, Mockito.times(1)).sendSignupConfirmationEmail(any(), any());
        Assertions.assertEquals(204, verifyEmail(email, code), "the code of the signup was not replaced");
    }

    @Test
    void the_answer_does_not_tell_whether_the_address_is_pending_registered_or_unknown() {
        String pending = newPseudo();
        String pendingEmail = pending + "@test.fr";
        signUp(pending, pendingEmail);
        pretendTheLastMailWasSent(pendingEmail, "2 minutes");
        TestAccount registered = registerAndLogin();

        ResponseEntity<String> forPending = call(HttpMethod.POST, "/accounts/verification-email", null, Map.of("email", pendingEmail));
        ResponseEntity<String> forRegistered = call(HttpMethod.POST, "/accounts/verification-email", null,
                Map.of("email", registered.pseudo() + "@test.fr"));
        ResponseEntity<String> forUnknown = call(HttpMethod.POST, "/accounts/verification-email", null, Map.of("email", "nobody" + pendingEmail));

        Assertions.assertEquals(202, forPending.getStatusCode().value());
        Assertions.assertEquals(forPending.getStatusCode(), forRegistered.getStatusCode());
        Assertions.assertEquals(forPending.getStatusCode(), forUnknown.getStatusCode());
        Assertions.assertEquals(forPending.getBody(), forRegistered.getBody());
        Assertions.assertEquals(forPending.getBody(), forUnknown.getBody());
    }

    @Test
    void nothing_is_sent_to_an_unknown_address_or_to_one_that_already_has_an_account() {
        TestAccount verified = registerAndLogin();
        Mockito.reset(emailingSpi);

        Assertions.assertEquals(202, resend("nobody" + UUID.randomUUID() + "@test.fr"));
        Assertions.assertEquals(202, resend(verified.pseudo() + "@test.fr"));

        Mockito.verifyNoInteractions(emailingSpi);
    }

    @Test
    void an_address_that_is_not_an_address_is_a_bad_request() {
        Assertions.assertEquals(400, resend("not-an-address"));
        Assertions.assertEquals(400, resend(""));
    }

    // ================= a mail server that is down =================

    @Test
    void a_registration_succeeds_even_when_the_mail_cannot_be_sent() {
        String pseudo = newPseudo();
        Mockito.doThrow(new EmailNotSendException()).when(emailingSpi).sendSignupConfirmationEmail(any(), any());

        Assertions.assertEquals(202, register(pseudo, pseudo + "@test.fr").getStatusCode().value());

        // nothing is lost: the pending signup is there, and the person can sign up again after the cooldown
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from pending_signups where pseudo = ?", Integer.class, pseudo));
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from accounts where pseudo = ?", Integer.class, pseudo));
    }

    @Test
    void a_mail_server_that_is_down_is_not_reported_to_whoever_asks_for_a_new_mail() {
        String pseudo = newPseudo();
        String email = pseudo + "@test.fr";
        signUp(pseudo, email);
        pretendTheLastMailWasSent(email, "2 minutes");
        Mockito.doThrow(new EmailNotSendException()).when(emailingSpi).sendSignupConfirmationEmail(any(), any());

        Assertions.assertEquals(202, resend(email));
    }

    // ================= the pseudo goes into the mails =================

    @Test
    void a_pseudo_with_markup_is_refused_at_registration() {
        for (String pseudo : List.of("<b>bob</b>", "<script>alert(1)</script>", "bob<a href=x>", "bob smith", "bob\r\nBcc: x", "ab", "a".repeat(31))) {
            Assertions.assertEquals(400, register(pseudo, "x" + UUID.randomUUID().toString().substring(0, 8) + "@test.fr").getStatusCode().value(), pseudo);
        }
    }

    @Test
    void a_pseudo_with_accents_and_symbols_that_are_harmless_is_accepted() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);

        Assertions.assertEquals(202, register("José_Zoë-" + suffix + ".1", "accent" + suffix + "@test.fr").getStatusCode().value());
    }

    @Test
    void a_pseudo_with_markup_is_refused_when_the_account_is_changed_too() {
        TestAccount account = registerAndLogin();

        Assertions.assertEquals(400, call(HttpMethod.PATCH, "/accounts", account.token(),
                Map.of("currentPassword", account.password(), "pseudo", "<b>bob</b>")).getStatusCode().value());
        Assertions.assertEquals(200, call(HttpMethod.PATCH, "/accounts", account.token(),
                Map.of("currentPassword", account.password(), "pseudo", "Zoë" + account.pseudo())).getStatusCode().value());
    }
}
