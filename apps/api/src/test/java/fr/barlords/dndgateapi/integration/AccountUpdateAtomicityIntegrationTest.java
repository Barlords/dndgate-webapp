package fr.barlords.dndgateapi.integration;

import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.SessionPersistenceSpi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * A change of the account is one unit: a new password never survives without the end of the old sessions, and a
 * change made from a stale read of the account is refused instead of erasing what was written meanwhile.
 */
class AccountUpdateAtomicityIntegrationTest extends ApiIntegrationTestSupport {

    @MockitoSpyBean
    SessionPersistenceSpi sessions;

    @MockitoSpyBean
    AccountPersistenceSpi accounts;

    @AfterEach
    void restore() {
        Mockito.reset(sessions, accounts);
    }

    private String passwordHash(TestAccount account) {
        return jdbc.queryForObject("select password from accounts where id = ?", String.class, account.id());
    }

    @Test
    void the_new_password_is_not_kept_when_the_sessions_cannot_be_ended() {
        TestAccount account = registerAndLogin();
        String hashBefore = passwordHash(account);
        Mockito.doThrow(new IllegalStateException("simulated failure"))
                .when(sessions).revokeAllSessionsOfAccount(eq(account.id()), any());

        ResponseEntity<String> response = call(HttpMethod.PATCH, "/accounts", account.token(),
                Map.of("currentPassword", account.password(), "newPassword", "another-password-1"));

        Assertions.assertEquals(500, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals(hashBefore, passwordHash(account), "the password must not change without the sessions ending");
        Assertions.assertEquals(200, call(HttpMethod.POST, "/accounts/login", null,
                Map.of("pseudo", account.pseudo(), "password", account.password())).getStatusCode().value());
    }

    @Test
    void an_account_changed_between_the_read_and_the_write_answers_409_and_keeps_the_other_change() {
        TestAccount account = registerAndLogin();
        AtomicBoolean armed = new AtomicBoolean(true);
        // right after the update reads the account, someone else changes it (for instance an e-mail confirmation)
        Mockito.doAnswer(invocation -> {
            Object read = invocation.callRealMethod();
            if (armed.getAndSet(false)) {
                // from another thread: on this one, the update would join the transaction of the request
                CompletableFuture.runAsync(() -> jdbc.update(
                        "update accounts set pending_email = 'other@test.fr', version = version + 1 where id = ?", account.id()))
                        .get(60, TimeUnit.SECONDS);
            }
            return read;
        }).when(accounts).findById(account.id());

        ResponseEntity<String> response = call(HttpMethod.PATCH, "/accounts", account.token(),
                Map.of("currentPassword", account.password(), "pseudo", "renamed" + UUID.randomUUID().toString().substring(0, 8)));

        Assertions.assertEquals(409, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals(account.pseudo(), jdbc.queryForObject("select pseudo from accounts where id = ?", String.class, account.id()));
        Assertions.assertEquals("other@test.fr", jdbc.queryForObject("select pending_email from accounts where id = ?", String.class, account.id()));
    }
}
