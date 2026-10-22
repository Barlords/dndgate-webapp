package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.PendingSignup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Registering does not tell whether an address is already registered (review v2, points 31 and 59): the answer is the
 * same either way, no account is created (only a pending signup), and the owner of the address is the one who hears
 * about the attempt.
 */
class SignupEnumerationIntegrationTest extends ApiIntegrationTestSupport {

    private ResponseEntity<String> register(String pseudo, String email) {
        return call(HttpMethod.POST, "/accounts", null, Map.of("email", email, "password", "password123", "pseudo", pseudo));
    }

    private String newPseudo() {
        return "enum" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Creates an activated account that owns the address, and returns its pseudo. */
    private String ownerOf(String email) {
        String pseudo = newPseudo();
        insertAccount(pseudo, email, "password123", true);
        return pseudo;
    }

    private int count(String table, String column, String value) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?", Integer.class, value);
    }

    private boolean pseudoIsAvailable(String pseudo) {
        TestAccount asker = registerAndLogin();
        int status = call(HttpMethod.GET, "/accounts/pseudo/" + pseudo, asker.token(), null).getStatusCode().value();
        Assertions.assertTrue(status == 200 || status == 404, String.valueOf(status));
        return status == 404;
    }

    @Test
    void a_registered_address_gets_the_same_answer_as_a_new_one() {
        String email = "owner" + newPseudo() + "@mail.com";
        ownerOf(email);

        ResponseEntity<String> fresh = register(newPseudo(), "free" + newPseudo() + "@mail.com");
        ResponseEntity<String> taken = register(newPseudo(), email);

        Assertions.assertEquals(202, fresh.getStatusCode().value());
        Assertions.assertEquals(fresh.getStatusCode(), taken.getStatusCode());
        Assertions.assertEquals(fresh.getBody(), taken.getBody());
        Assertions.assertEquals(stableHeaders(fresh), stableHeaders(taken));
    }

    @Test
    void the_answer_is_a_fixed_message_without_any_account_data() {
        ResponseEntity<String> response = register(newPseudo(), "fixed" + newPseudo() + "@mail.com");

        Assertions.assertEquals(202, response.getStatusCode().value());
        Assertions.assertEquals(1, body(response).size());
        Assertions.assertEquals("One last step before your account is validated: check your e-mail address",
                body(response).get("message").asString());
    }

    /** The headers that do not change from one request to the next: not the date, the correlation id, the counters. */
    private static Map<String, List<String>> stableHeaders(ResponseEntity<String> response) {
        Map<String, List<String>> kept = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        response.getHeaders().forEach((name, values) -> {
            String lower = name.toLowerCase();
            if (!lower.equals("date") && !lower.contains("correlation") && !lower.equals("x-request-id") && !lower.contains("ratelimit")
                    && !lower.contains("rate-limit") && !lower.equals("retry-after")) {
                kept.put(name, values);
            }
        });
        return kept;
    }

    @Test
    void a_taken_pseudo_answers_409_the_same_whether_the_address_is_registered_or_free() {
        String owner = ownerOf("a" + newPseudo() + "@mail.com");
        String email = "b" + newPseudo() + "@mail.com";
        ownerOf(email);
        Mockito.clearInvocations(emailingSpi);

        ResponseEntity<String> registeredAddress = register(owner, email);
        ResponseEntity<String> freeAddress = register(owner, "free-" + newPseudo() + "@mail.com");

        Assertions.assertEquals(409, registeredAddress.getStatusCode().value());
        Assertions.assertEquals(registeredAddress.getStatusCode(), freeAddress.getStatusCode());
        Assertions.assertEquals(body(registeredAddress).get("detail"), body(freeAddress).get("detail"));
        Mockito.verify(emailingSpi, Mockito.never()).sendSignupAttemptEmail(ArgumentMatchers.any());
        Mockito.verify(emailingSpi, Mockito.never()).sendSignupConfirmationEmail(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void signing_up_never_creates_an_account() {
        String pseudo = newPseudo();
        String email = pseudo + "@mail.com";

        Assertions.assertEquals(202, register(pseudo, email).getStatusCode().value());

        Assertions.assertEquals(0, count("accounts", "pseudo", pseudo));
        Assertions.assertEquals(0, count("accounts", "email", email));
        Assertions.assertEquals(1, count("pending_signups", "email", email));
    }

    @Test
    void a_registered_address_gets_no_second_account_no_pending_signup_and_no_confirmation_mail() {
        String email = "owner" + newPseudo() + "@mail.com";
        ownerOf(email);
        Mockito.clearInvocations(emailingSpi);
        String other = newPseudo();

        register(other, email);

        Assertions.assertEquals(1, count("accounts", "email", email));
        Assertions.assertEquals(0, count("accounts", "pseudo", other));
        Assertions.assertEquals(0, count("pending_signups", "email", email));
        Mockito.verify(emailingSpi, Mockito.never()).sendSignupConfirmationEmail(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void the_owner_of_the_address_is_told_about_the_attempt() {
        String email = "owner" + newPseudo() + "@mail.com";
        String owner = ownerOf(email);
        Mockito.clearInvocations(emailingSpi);

        register(newPseudo(), email.replace("@mail.com", "@Mail.com"));

        ArgumentCaptor<Account> told = ArgumentCaptor.forClass(Account.class);
        Mockito.verify(emailingSpi).sendSignupAttemptEmail(told.capture());
        Assertions.assertEquals(owner, told.getValue().getPseudo());
        Assertions.assertEquals(email, told.getValue().getEmail());
    }

    @Test
    void a_new_address_is_not_told_about_an_attempt_and_gets_the_confirmation_link() {
        String pseudo = newPseudo();
        Mockito.clearInvocations(emailingSpi);

        register(pseudo, pseudo + "@mail.com");

        Mockito.verify(emailingSpi, Mockito.never()).sendSignupAttemptEmail(ArgumentMatchers.any());
        ArgumentCaptor<PendingSignup> signup = ArgumentCaptor.forClass(PendingSignup.class);
        Mockito.verify(emailingSpi).sendSignupConfirmationEmail(signup.capture(), ArgumentMatchers.any());
        Assertions.assertEquals(pseudo + "@mail.com", signup.getValue().getEmail());
        Assertions.assertEquals(pseudo, signup.getValue().getPseudo());
    }

    @Test
    void a_pseudo_already_taken_by_an_account_still_answers_409_because_pseudos_are_public() {
        String owner = ownerOf("o" + newPseudo() + "@mail.com");

        Assertions.assertEquals(409, register(owner, "other-" + owner + "@mail.com").getStatusCode().value());
    }

    @Test
    void the_two_request_probe_cannot_tell_a_registered_address_from_a_free_one() {
        String registeredEmail = "reg" + newPseudo() + "@mail.com";
        ownerOf(registeredEmail);
        String probeOnRegistered = newPseudo();
        String probeOnFree = newPseudo();

        register(probeOnRegistered, registeredEmail);
        register(probeOnFree, "free" + newPseudo() + "@mail.com");

        // the fresh pseudo stays available in both cases, and signing up with it again is still accepted
        Assertions.assertTrue(pseudoIsAvailable(probeOnRegistered));
        Assertions.assertTrue(pseudoIsAvailable(probeOnFree));
        Assertions.assertEquals(202, register(probeOnRegistered, "again" + newPseudo() + "@mail.com").getStatusCode().value());
        Assertions.assertEquals(202, register(probeOnFree, "again" + newPseudo() + "@mail.com").getStatusCode().value());
    }

    @Test
    void the_pseudo_of_a_pending_signup_is_not_reserved() {
        String pseudo = newPseudo();
        register(pseudo, "first" + newPseudo() + "@mail.com");

        Assertions.assertTrue(pseudoIsAvailable(pseudo));
        Assertions.assertEquals(202, register(pseudo, "second" + newPseudo() + "@mail.com").getStatusCode().value());
    }
}
