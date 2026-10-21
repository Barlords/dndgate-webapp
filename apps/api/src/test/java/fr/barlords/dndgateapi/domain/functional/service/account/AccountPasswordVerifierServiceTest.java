package fr.barlords.dndgateapi.domain.functional.service.account;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import fr.barlords.dndgateapi.domain.functional.exception.account.AccountLockedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.WrongPasswordException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;
import fr.barlords.dndgateapi.domain.functional.model.account.LoginFailures;
import fr.barlords.dndgateapi.domain.port.out.LoginFailureSpi;
import fr.barlords.dndgateapi.domain.port.out.PasswordHasherSpi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountPasswordVerifierServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static final int MAX_ATTEMPTS = 5;

    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private static final int WARNING_THRESHOLD = 50;

    private static final ClientAddress CLIENT_A = new ClientAddress("203.0.113.7");

    private static final ClientAddress CLIENT_B = new ClientAddress("198.51.100.9");

    @Mock
    private LoginFailureSpi failures;

    @Mock
    private PasswordHasherSpi passwordHasher;

    private AccountPasswordVerifierService service;

    private ListAppender<ILoggingEvent> logs;

    private final Account account = Account.builder().pseudo("usertest").email("test@test.fr").password("hash").build();

    @BeforeEach
    void setUp() {
        service = new AccountPasswordVerifierService(
                failures, passwordHasher, Clock.fixed(NOW, ZoneOffset.UTC), MAX_ATTEMPTS, LOCK_DURATION, WARNING_THRESHOLD);
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(AccountPasswordVerifierService.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(AccountPasswordVerifierService.class)).detachAppender(logs);
    }

    @Test
    void the_right_password_returns_the_account_and_leaves_the_failures_alone_when_there_are_none() {
        when(passwordHasher.matches("password", "hash")).thenReturn(true);

        Assertions.assertEquals(account, service.verify(account, "password", CLIENT_A, WrongPasswordException::new));
        verify(failures, never()).register(any(), any(), anyInt(), any(), any());
        verify(failures, never()).clear(any(), any());
    }

    @Test
    void a_wrong_password_is_counted_against_the_client_and_throws_what_the_caller_asked() {
        when(passwordHasher.matches("wrong", "hash")).thenReturn(false);

        Assertions.assertThrows(WrongPasswordException.class, () -> service.verify(account, "wrong", CLIENT_A, WrongPasswordException::new));
        Assertions.assertThrows(IllegalStateException.class, () -> service.verify(account, "wrong", CLIENT_A, IllegalStateException::new));

        verify(failures, times(2)).register(account.getId(), CLIENT_A, MAX_ATTEMPTS, NOW.plus(LOCK_DURATION), NOW);
    }

    @Test
    void a_locked_client_is_refused_even_with_the_right_password() {
        Instant lockedUntil = NOW.plusSeconds(300);
        when(failures.find(account.getId(), CLIENT_A)).thenReturn(Optional.of(new LoginFailures(MAX_ATTEMPTS, lockedUntil)));

        AccountLockedException exception = Assertions.assertThrows(AccountLockedException.class,
                () -> service.verify(account, "right-password", CLIENT_A, WrongPasswordException::new));

        Assertions.assertEquals(lockedUntil, exception.getLockedUntil());
        verify(passwordHasher, never()).matches(any(), any());
        verify(failures, never()).register(any(), any(), anyInt(), any(), any());
    }

    @Test
    void the_lock_of_one_client_does_not_concern_another_one() {
        when(failures.find(account.getId(), CLIENT_A)).thenReturn(Optional.of(new LoginFailures(MAX_ATTEMPTS, NOW.plusSeconds(300))));
        when(failures.find(account.getId(), CLIENT_B)).thenReturn(Optional.empty());
        when(passwordHasher.matches("password", "hash")).thenReturn(true);

        Assertions.assertThrows(AccountLockedException.class,
                () -> service.verify(account, "password", CLIENT_A, WrongPasswordException::new));
        Assertions.assertEquals(account, service.verify(account, "password", CLIENT_B, WrongPasswordException::new));
    }

    @Test
    void a_wrong_password_from_another_client_is_counted_for_that_client_only() {
        when(passwordHasher.matches("wrong", "hash")).thenReturn(false);

        Assertions.assertThrows(WrongPasswordException.class, () -> service.verify(account, "wrong", CLIENT_B, WrongPasswordException::new));

        verify(failures).register(account.getId(), CLIENT_B, MAX_ATTEMPTS, NOW.plus(LOCK_DURATION), NOW);
        verify(failures, never()).register(any(), eq(CLIENT_A), anyInt(), any(), any());
    }

    @Test
    void once_the_lock_has_expired_the_client_can_try_again() {
        when(failures.find(account.getId(), CLIENT_A)).thenReturn(Optional.of(new LoginFailures(MAX_ATTEMPTS, NOW.minusSeconds(1))));
        when(passwordHasher.matches("password", "hash")).thenReturn(true);

        Assertions.assertEquals(account, service.verify(account, "password", CLIENT_A, WrongPasswordException::new));

        verify(failures).clear(account.getId(), CLIENT_A);
    }

    @Test
    void a_success_clears_the_previous_failures_of_the_client() {
        when(failures.find(account.getId(), CLIENT_A)).thenReturn(Optional.of(new LoginFailures(3, null)));
        when(passwordHasher.matches("password", "hash")).thenReturn(true);

        service.verify(account, "password", CLIENT_A, WrongPasswordException::new);

        verify(failures).clear(account.getId(), CLIENT_A);
    }

    @Test
    void failures_from_many_different_clients_are_logged_as_a_warning_but_nothing_is_blocked() {
        when(passwordHasher.matches("wrong", "hash")).thenReturn(false);
        when(failures.countClientsFailingSince(account.getId(), NOW.minus(LOCK_DURATION))).thenReturn(WARNING_THRESHOLD);

        Assertions.assertThrows(WrongPasswordException.class, () -> service.verify(account, "wrong", CLIENT_A, WrongPasswordException::new));

        List<ILoggingEvent> warnings = logs.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
        Assertions.assertEquals(1, warnings.size());
        String message = warnings.get(0).getFormattedMessage();
        Assertions.assertTrue(message.contains(account.getId().toString()));
        Assertions.assertTrue(message.contains(String.valueOf(WARNING_THRESHOLD)));
        Assertions.assertFalse(message.contains(CLIENT_A.value()), "the address is personal data and stays out of the logs");
        Assertions.assertFalse(message.contains("wrong"), "no secret in the logs");
    }

    @Test
    void no_warning_below_the_threshold() {
        when(passwordHasher.matches("wrong", "hash")).thenReturn(false);
        when(failures.countClientsFailingSince(account.getId(), NOW.minus(LOCK_DURATION))).thenReturn(WARNING_THRESHOLD - 1);

        Assertions.assertThrows(WrongPasswordException.class, () -> service.verify(account, "wrong", CLIENT_A, WrongPasswordException::new));

        Assertions.assertTrue(logs.list.stream().noneMatch(e -> e.getLevel() == Level.WARN));
    }

    @Test
    void the_cleaner_forgets_the_failures_older_than_the_lock_window() {
        new LoginFailureCleanerService(failures, Clock.fixed(NOW, ZoneOffset.UTC), LOCK_DURATION).purgeExpired();

        verify(failures).deleteFailedBefore(NOW.minus(LOCK_DURATION));
    }

}
