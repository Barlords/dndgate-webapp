package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.account.AccountCredentialNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.account.AccountLockedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.AccountNotValidatedException;
import fr.barlords.dndgateapi.domain.functional.model.account.AccessToken;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.AccountSession;
import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;
import fr.barlords.dndgateapi.domain.functional.model.account.IssuedRefreshToken;
import fr.barlords.dndgateapi.domain.functional.model.account.LoginFailures;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.LoginFailureSpi;
import fr.barlords.dndgateapi.domain.port.out.PasswordHasherSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountLoggerServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static final int MAX_ATTEMPTS = 5;

    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private static final ClientAddress CLIENT = new ClientAddress("203.0.113.7");

    @Mock
    private AccountPersistenceSpi spi;

    @Mock
    private LoginFailureSpi failures;

    @Mock
    private PasswordHasherSpi passwordHasher;

    @Mock
    private SessionIssuerService sessionIssuer;

    private AccountLoggerService service;

    @BeforeEach
    void setUp() {
        when(passwordHasher.hash(any())).thenReturn("unknown-account-hash");
        AccountPasswordVerifierService passwordVerifier = new AccountPasswordVerifierService(
                failures, passwordHasher, Clock.fixed(NOW, ZoneOffset.UTC), MAX_ATTEMPTS, LOCK_DURATION, 50);
        service = new AccountLoggerService(spi, passwordHasher, passwordVerifier, sessionIssuer, new AccountCheckerService(spi));
    }

    private Account account(boolean activated) {
        return Account.builder().pseudo("usertest").email("test@test.fr").password("hash").activated(activated).build();
    }

    @Test
    void an_unknown_pseudo_is_refused_after_a_password_comparison() {
        when(spi.findByPseudo("unknown")).thenReturn(Optional.empty());

        Assertions.assertThrows(AccountCredentialNotFoundException.class, () -> service.login("unknown", "password", CLIENT));

        // same work as for a known pseudo, so the response time does not reveal which pseudos exist
        verify(passwordHasher).matches("password", "unknown-account-hash");
        verify(failures, never()).register(any(), any(), anyInt(), any(), any());
    }

    @Test
    void a_wrong_password_is_counted_for_the_client_and_refused() {
        Account account = account(true);
        when(spi.findByPseudo("usertest")).thenReturn(Optional.of(account));
        when(passwordHasher.matches("wrong", "hash")).thenReturn(false);

        Assertions.assertThrows(AccountCredentialNotFoundException.class, () -> service.login("usertest", "wrong", CLIENT));

        verify(failures).register(account.getId(), CLIENT, MAX_ATTEMPTS, NOW.plus(LOCK_DURATION), NOW);
        verify(sessionIssuer, never()).openSession(any());
    }

    @Test
    void a_client_that_is_locked_is_refused_even_with_the_right_password() {
        Account account = account(true);
        Instant lockedUntil = NOW.plusSeconds(300);
        when(spi.findByPseudo("usertest")).thenReturn(Optional.of(account));
        when(failures.find(account.getId(), CLIENT)).thenReturn(Optional.of(new LoginFailures(MAX_ATTEMPTS, lockedUntil)));

        AccountLockedException exception = Assertions.assertThrows(AccountLockedException.class,
                () -> service.login("usertest", "right-password", CLIENT));

        Assertions.assertEquals(lockedUntil, exception.getLockedUntil());
        verify(passwordHasher, never()).matches("right-password", "hash");
        verify(sessionIssuer, never()).openSession(any());
    }

    @Test
    void a_right_password_on_a_not_activated_account_is_refused_without_counting() {
        when(spi.findByPseudo("usertest")).thenReturn(Optional.of(account(false)));
        when(passwordHasher.matches("password", "hash")).thenReturn(true);

        Assertions.assertThrows(AccountNotValidatedException.class, () -> service.login("usertest", "password", CLIENT));

        verify(failures, never()).register(any(), any(), anyInt(), any(), any());
        verify(sessionIssuer, never()).openSession(any());
    }

    @Test
    void a_successful_login_opens_a_session_and_clears_the_previous_failures_of_the_client() {
        Account account = account(true);
        AccountSession session = new AccountSession(account, new AccessToken("jwt", NOW), new IssuedRefreshToken("rt", NOW));
        when(spi.findByPseudo("usertest")).thenReturn(Optional.of(account));
        when(failures.find(account.getId(), CLIENT)).thenReturn(Optional.of(new LoginFailures(3, null)));
        when(passwordHasher.matches("password", "hash")).thenReturn(true);
        when(sessionIssuer.openSession(account)).thenReturn(session);

        Assertions.assertEquals(session, service.login("usertest", "password", CLIENT));

        verify(failures).clear(account.getId(), CLIENT);
    }

}
