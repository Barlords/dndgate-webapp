package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.functional.exception.account.EmailAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.PseudoAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.in.account.SignupNoticeCleanerApi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

/**
 * At most one "someone tried to register with your address" notice per address and per window (review v2, point 31),
 * counted in the database, and a registration race ends like any other taken address.
 */
class SignupNoticeLimitIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    SignupNoticeCleanerApi cleaner;

    @Autowired
    AccountPersistenceSpi accounts;

    private ResponseEntity<String> register(String pseudo, String email) {
        return call(HttpMethod.POST, "/accounts", null, Map.of("email", email, "password", "password123", "pseudo", pseudo));
    }

    private String newPseudo() {
        return "notice" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void attempts(String email, int count) {
        for (int i = 0; i < count; i++) {
            Assertions.assertEquals(202, register(newPseudo(), email).getStatusCode().value());
        }
    }

    private static String sha256(String text) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private int notices() {
        return Mockito.mockingDetails(emailingSpi).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("sendSignupAttemptEmail")).toList().size();
    }

    @Test
    void a_second_attempt_inside_the_window_sends_no_second_notice_and_answers_the_same() {
        String owner = newPseudo();
        createAccount(owner, owner + "@mail.com", "password123");
        Mockito.clearInvocations(emailingSpi);

        ResponseEntity<String> first = register(newPseudo(), owner + "@mail.com");
        ResponseEntity<String> second = register(newPseudo(), owner + "@mail.com");

        Assertions.assertEquals(1, notices());
        Assertions.assertEquals(202, second.getStatusCode().value());
        Assertions.assertEquals(first.getStatusCode(), second.getStatusCode());
        Assertions.assertEquals(first.getBody(), second.getBody());
    }

    @Test
    void an_attempt_after_the_window_sends_a_new_notice() {
        String owner = newPseudo();
        createAccount(owner, owner + "@mail.com", "password123");
        Mockito.clearInvocations(emailingSpi);
        attempts(owner + "@mail.com", 1);
        jdbc.update("update signup_notices set sent_at = now() - interval '25 hours' where email_hash = ?", sha256(owner + "@mail.com"));

        attempts(owner + "@mail.com", 1);

        Assertions.assertEquals(2, notices());
    }

    @Test
    void the_limit_is_per_address() {
        String first = newPseudo();
        String second = newPseudo();
        createAccount(first, first + "@mail.com", "password123");
        createAccount(second, second + "@mail.com", "password123");
        Mockito.clearInvocations(emailingSpi);

        attempts(first + "@mail.com", 1);
        attempts(second + "@mail.com", 1);

        Assertions.assertEquals(2, notices());
    }

    @Test
    void the_address_is_not_stored_in_clear() {
        String owner = newPseudo();
        createAccount(owner, owner + "@mail.com", "password123");
        attempts(owner + "@mail.com", 1);

        Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from signup_notices where email_hash = ?", Integer.class, sha256(owner + "@mail.com")));
        Assertions.assertEquals(0, jdbc.queryForObject(
                "select count(*) from signup_notices where email_hash like '%mail.com%'", Integer.class));
    }

    @Test
    void the_purge_forgets_old_notices_and_keeps_recent_ones() {
        jdbc.update("insert into signup_notices (email_hash, sent_at) values ('old-one', now() - interval '25 hours')");
        jdbc.update("insert into signup_notices (email_hash, sent_at) values ('recent-one', now() - interval '1 hour')");

        cleaner.purgeExpired();

        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from signup_notices where email_hash = 'old-one'", Integer.class));
        Assertions.assertEquals(1, jdbc.queryForObject("select count(*) from signup_notices where email_hash = 'recent-one'", Integer.class));
    }

    @Test
    void a_taken_address_with_a_taken_pseudo_answers_409_because_the_pseudo_is_public() {
        String owner = newPseudo();
        createAccount(owner, owner + "@mail.com", "password123");

        ResponseEntity<String> response = register(owner, owner + "@mail.com");

        Assertions.assertEquals(409, response.getStatusCode().value(), response.getBody());
    }

    @Test
    void the_database_refusal_of_a_duplicate_address_is_a_taken_address_not_a_500() {
        String owner = newPseudo();
        createAccount(owner, owner + "@mail.com", "password123");

        Account duplicate = Account.builder().pseudo(newPseudo()).email(owner + "@mail.com").password("hash").build();

        Assertions.assertThrows(EmailAlreadyUsedException.class, () -> accounts.save(duplicate));
    }

    @Test
    void the_constraint_is_read_from_its_name_not_from_the_refused_value() {
        // The refused value ends up in the message of the database: it must not be taken for a constraint name
        String name = "accounts_email_key";
        accounts.save(Account.builder().pseudo(name).email("odd-" + newPseudo() + "@mail.com").password("hash").build());

        Account duplicate = Account.builder().pseudo(name).email("odd2-" + newPseudo() + "@mail.com").password("hash").build();

        Assertions.assertThrows(PseudoAlreadyUsedException.class, () -> accounts.save(duplicate));
    }

    @Test
    void the_database_refusal_of_a_duplicate_pseudo_is_a_taken_pseudo() {
        String owner = newPseudo();
        createAccount(owner, owner + "@mail.com", "password123");

        Account duplicate = Account.builder().pseudo(owner).email("other-" + owner + "@mail.com").password("hash").build();

        Assertions.assertThrows(PseudoAlreadyUsedException.class, () -> accounts.save(duplicate));
    }
}
