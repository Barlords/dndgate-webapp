package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.functional.exception.EmailNotSendException;
import fr.barlords.dndgateapi.domain.functional.model.account.PendingSignup;
import fr.barlords.dndgateapi.domain.port.in.account.PendingSignupCleanerApi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * What a pending signup is (review v2, point 59): one per address, replaced only after the cooldown, its code stored
 * as a hash, and forgotten once it expired.
 */
class SignupPendingIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    PendingSignupCleanerApi cleaner;

    private ResponseEntity<String> register(String pseudo, String email) {
        return call(HttpMethod.POST, "/accounts", null, Map.of("email", email, "password", "password123", "pseudo", pseudo));
    }

    private String newPseudo() {
        return "pend" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Map<String, Object> row(String email) {
        return jdbc.queryForMap("select * from pending_signups where email = ?", email);
    }

    private int rows(String email) {
        return jdbc.queryForObject("select count(*) from pending_signups where email = ?", Integer.class, email);
    }

    /** The clear code of the last confirmation mail that was sent. */
    private String codeOfTheLastMail() {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        Mockito.verify(emailingSpi, Mockito.atLeastOnce()).sendSignupConfirmationEmail(ArgumentMatchers.any(), code.capture());
        return code.getValue();
    }

    private void pretendTheSignupWasMade(String email, String ago) {
        jdbc.update("update pending_signups set created_at = now() - interval '" + ago + "' where email = ?", email);
    }

    @Test
    void the_signup_is_recorded_with_the_normalised_address_a_password_hash_and_a_24_hour_expiry() {
        String pseudo = newPseudo();

        Assertions.assertEquals(202, register(pseudo, pseudo + "@Mail.COM").getStatusCode().value());

        Map<String, Object> row = row(pseudo + "@mail.com");
        Assertions.assertEquals(pseudo, row.get("pseudo"));
        Assertions.assertTrue(String.valueOf(row.get("password_hash")).startsWith("$2"), "a bcrypt hash, never the password");
        Assertions.assertNotEquals("password123", row.get("password_hash"));
        Instant created = ((java.sql.Timestamp) row.get("created_at")).toInstant();
        Instant expires = ((java.sql.Timestamp) row.get("expires_at")).toInstant();
        Assertions.assertEquals(java.time.Duration.ofHours(24), java.time.Duration.between(created, expires));
    }

    @Test
    void the_code_is_stored_as_its_sha_256_and_never_in_clear() {
        String pseudo = newPseudo();
        String email = pseudo + "@mail.com";
        register(pseudo, email);

        String clear = codeOfTheLastMail();
        String stored = (String) row(email).get("code_hash");

        Assertions.assertEquals(24, clear.length());
        Assertions.assertNotEquals(clear, stored);
        Assertions.assertEquals(PendingSignup.hashOf(clear), stored);
        Assertions.assertEquals(64, stored.length());
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from pending_signups where code_hash = ? or password_hash = ?", Integer.class, clear, clear));
    }

    @Test
    void a_second_signup_inside_the_cooldown_is_ignored_in_silence() {
        String email = newPseudo() + "@mail.com";
        String firstPseudo = newPseudo();
        register(firstPseudo, email);
        Map<String, Object> before = row(email);
        Mockito.clearInvocations(emailingSpi);

        ResponseEntity<String> second = register(newPseudo(), email);

        Assertions.assertEquals(202, second.getStatusCode().value());
        Assertions.assertEquals(1, rows(email));
        Assertions.assertEquals(before, row(email), "nothing changed");
        Mockito.verify(emailingSpi, Mockito.never()).sendSignupConfirmationEmail(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void a_signup_after_the_cooldown_replaces_the_previous_one_and_sends_a_new_mail() {
        String email = newPseudo() + "@mail.com";
        register(newPseudo(), email);
        String oldCodeHash = (String) row(email).get("code_hash");
        pretendTheSignupWasMade(email, "2 minutes");
        Mockito.clearInvocations(emailingSpi);
        String newPseudo = newPseudo();

        Assertions.assertEquals(202, register(newPseudo, email).getStatusCode().value());

        Map<String, Object> row = row(email);
        Assertions.assertEquals(1, rows(email));
        Assertions.assertEquals(newPseudo, row.get("pseudo"));
        Assertions.assertNotEquals(oldCodeHash, row.get("code_hash"), "a new code: the old link no longer works");
        Assertions.assertEquals(PendingSignup.hashOf(codeOfTheLastMail()), row.get("code_hash"));
        Mockito.verify(emailingSpi).sendSignupConfirmationEmail(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void simultaneous_signups_for_the_same_address_all_get_202_and_leave_one_pending_signup_and_one_mail() throws Exception {
        String email = newPseudo() + "@mail.com";
        Mockito.clearInvocations(emailingSpi);
        List<Callable<Integer>> signups = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String pseudo = newPseudo();
            signups.add(() -> register(pseudo, email).getStatusCode().value());
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (Future<Integer> status : pool.invokeAll(signups)) {
                Assertions.assertEquals(202, status.get());
            }
        } finally {
            pool.shutdownNow();
        }

        Assertions.assertEquals(1, rows(email));
        Mockito.verify(emailingSpi, Mockito.times(1)).sendSignupConfirmationEmail(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void a_mail_that_cannot_be_sent_does_not_change_the_answer() {
        String pseudo = newPseudo();
        String email = pseudo + "@mail.com";
        Mockito.doThrow(new EmailNotSendException()).when(emailingSpi)
                .sendSignupConfirmationEmail(ArgumentMatchers.any(), ArgumentMatchers.any());

        ResponseEntity<String> response = register(pseudo, email);

        Assertions.assertEquals(202, response.getStatusCode().value());
        Assertions.assertEquals("One last step before your account is validated: check your e-mail address",
                body(response).get("message").asString());
        Assertions.assertEquals(1, rows(email));
        Mockito.reset(emailingSpi);
    }

    @Test
    void the_purge_forgets_the_expired_signups_and_keeps_the_others() {
        String expired = newPseudo() + "@mail.com";
        String alive = newPseudo() + "@mail.com";
        register(newPseudo(), expired);
        register(newPseudo(), alive);
        jdbc.update("update pending_signups set expires_at = now() - interval '1 minute' where email = ?", expired);

        cleaner.purgeExpired();

        Assertions.assertEquals(0, rows(expired));
        Assertions.assertEquals(1, rows(alive));
    }
}
