package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

/**
 * Registering does not tell whether an address is already registered (review v2, point 31): the answer is the same
 * either way, and the owner of the address is the one who hears about the attempt.
 */
class SignupEnumerationIntegrationTest extends ApiIntegrationTestSupport {

    private ResponseEntity<String> register(String pseudo, String email) {
        return call(HttpMethod.POST, "/accounts", null, Map.of("email", email, "password", "password123", "pseudo", pseudo));
    }

    private String newPseudo() {
        return "enum" + UUID.randomUUID().toString().substring(0, 8);
    }

    private int accountsWithEmail(String email) {
        return jdbc.queryForObject("select count(*) from accounts where email = ?", Integer.class, email);
    }

    @Test
    void a_registered_address_gets_the_same_answer_as_a_new_one() {
        String owner = newPseudo();
        ResponseEntity<String> fresh = register(owner, owner + "@mail.com");

        ResponseEntity<String> taken = register(newPseudo(), owner + "@mail.com");

        Assertions.assertEquals(202, fresh.getStatusCode().value());
        Assertions.assertEquals(fresh.getStatusCode(), taken.getStatusCode());
        Assertions.assertEquals(fresh.getBody(), taken.getBody());
        Assertions.assertEquals(fresh.getHeaders().getContentType(), taken.getHeaders().getContentType());
    }

    @Test
    void a_registered_address_gets_no_second_account_and_no_validation_mail() {
        String owner = newPseudo();
        register(owner, owner + "@mail.com");
        Mockito.clearInvocations(emailingSpi);
        String other = newPseudo();

        register(other, owner + "@mail.com");

        Assertions.assertEquals(1, accountsWithEmail(owner + "@mail.com"));
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from accounts where pseudo = ?", Integer.class, other));
        Mockito.verify(emailingSpi, Mockito.never()).sendAccountValidationEmail(ArgumentMatchers.any());
    }

    @Test
    void the_owner_of_the_address_is_told_about_the_attempt() {
        String owner = newPseudo();
        register(owner, owner + "@mail.com");
        Mockito.clearInvocations(emailingSpi);

        register(newPseudo(), owner + "@Mail.com");

        ArgumentCaptor<Account> told = ArgumentCaptor.forClass(Account.class);
        Mockito.verify(emailingSpi).sendSignupAttemptEmail(told.capture());
        Assertions.assertEquals(owner, told.getValue().getPseudo());
        Assertions.assertEquals(owner + "@mail.com", told.getValue().getEmail());
    }

    @Test
    void a_new_address_is_not_told_about_an_attempt() {
        String pseudo = newPseudo();
        Mockito.clearInvocations(emailingSpi);

        register(pseudo, pseudo + "@mail.com");

        Mockito.verify(emailingSpi, Mockito.never()).sendSignupAttemptEmail(ArgumentMatchers.any());
        Mockito.verify(emailingSpi).sendAccountValidationEmail(ArgumentMatchers.any());
    }

    @Test
    void a_pseudo_already_taken_still_answers_409_because_pseudos_are_public() {
        String owner = newPseudo();
        register(owner, owner + "@mail.com");

        Assertions.assertEquals(409, register(owner, "other-" + owner + "@mail.com").getStatusCode().value());
    }
}
