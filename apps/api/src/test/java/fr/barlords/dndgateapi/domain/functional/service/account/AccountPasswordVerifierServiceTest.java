package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.account.AccountLockedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.WrongPasswordException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountPasswordVerifierServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static final int MAX_ATTEMPTS = 5;

    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Mock
    private AccountPersistenceSpi spi;

    @Mock
    private PasswordHasherSpi passwordHasher;

    private AccountPasswordVerifierService service;

    private final Account account = Account.builder().pseudo("usertest").email("test@test.fr").password("hash").build();

    @BeforeEach
    void setUp() {
        service = new AccountPasswordVerifierService(spi, passwordHasher, Clock.fixed(NOW, ZoneOffset.UTC), MAX_ATTEMPTS, LOCK_DURATION);
    }

    @Test
    void the_right_password_returns_the_account() {
        when(passwordHasher.matches("password", "hash")).thenReturn(true);

        Assertions.assertEquals(account, service.verify(account, "password", WrongPasswordException::new));
        verify(spi, never()).registerFailedLogin(any(), anyInt(), any());
        verify(spi, never()).resetFailedLogins(any());
    }

    @Test
    void a_wrong_password_is_counted_and_throws_what_the_caller_asked() {
        when(passwordHasher.matches("wrong", "hash")).thenReturn(false);

        Assertions.assertThrows(WrongPasswordException.class, () -> service.verify(account, "wrong", WrongPasswordException::new));
        Assertions.assertThrows(IllegalStateException.class, () -> service.verify(account, "wrong", IllegalStateException::new));

        verify(spi, org.mockito.Mockito.times(2)).registerFailedLogin(account.getId(), MAX_ATTEMPTS, NOW.plus(LOCK_DURATION));
    }

    @Test
    void a_locked_account_is_refused_even_with_the_right_password() {
        Instant lockedUntil = NOW.plusSeconds(300);
        Account locked = account.withFailedLoginAttempts(MAX_ATTEMPTS).withLockedUntil(lockedUntil);

        AccountLockedException exception = Assertions.assertThrows(AccountLockedException.class,
                () -> service.verify(locked, "right-password", WrongPasswordException::new));

        Assertions.assertEquals(lockedUntil, exception.getLockedUntil());
        verify(passwordHasher, never()).matches(any(), any());
        verify(spi, never()).registerFailedLogin(any(), anyInt(), any());
    }

    @Test
    void once_the_lock_has_expired_the_counter_starts_from_zero() {
        Account expired = account.withFailedLoginAttempts(MAX_ATTEMPTS).withLockedUntil(NOW.minusSeconds(1));
        when(passwordHasher.matches("password", "hash")).thenReturn(true);

        Account result = service.verify(expired, "password", WrongPasswordException::new);

        verify(spi).resetFailedLogins(expired.getId());
        Assertions.assertEquals(0, result.getFailedLoginAttempts());
        Assertions.assertNull(result.getLockedUntil());
    }

    @Test
    void a_success_clears_the_previous_failures() {
        Account withFailures = account.withFailedLoginAttempts(3);
        when(passwordHasher.matches("password", "hash")).thenReturn(true);

        Account result = service.verify(withFailures, "password", WrongPasswordException::new);

        verify(spi).resetFailedLogins(withFailures.getId());
        Assertions.assertEquals(0, result.getFailedLoginAttempts());
    }
}
