package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.EmailNotSendException;
import fr.barlords.dndgateapi.domain.functional.exception.account.PseudoAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.PendingSignup;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import fr.barlords.dndgateapi.domain.port.out.PasswordHasherSpi;
import fr.barlords.dndgateapi.domain.port.out.PendingSignupPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.SignupNoticeLogSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Signing up (review v2, point 59): the request only records a pending signup, the account does not exist until the
 * link of the mail is followed.
 */
@ExtendWith(MockitoExtension.class)
class AccountSignupTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static final Duration TTL = Duration.ofHours(24);

    private static final Duration COOLDOWN = Duration.ofMinutes(1);

    private static final Duration NOTICE_WINDOW = Duration.ofHours(24);

    @Mock
    private AccountPersistenceSpi accounts;

    @Mock
    private PendingSignupPersistenceSpi pendingSignups;

    @Mock
    private EmailingSpi emailingSpi;

    @Mock
    private AccountCheckerService checker;

    @Mock
    private PasswordHasherSpi passwordHasher;

    @Mock
    private SignupNoticeLogSpi noticeLog;

    private AccountCreatorService service() {
        return new AccountCreatorService(accounts, checker, new AccountModifierService(passwordHasher), pendingSignups, emailingSpi,
                Clock.fixed(NOW, ZoneOffset.UTC), TTL, COOLDOWN, noticeLog, NOTICE_WINDOW);
    }

    private Account owner() {
        return Account.builder().pseudo("owner").email("test@test.fr").password("hash").activated(true).build();
    }

    private Account newcomer() {
        return Account.builder().pseudo("usertest").email("test@test.fr").password("clear").build();
    }

    @BeforeEach
    void setUp() {
        lenient().when(passwordHasher.hash(any())).thenReturn("hashed");
        lenient().when(pendingSignups.saveUnlessRecent(any(), any())).thenReturn(true);
    }

    private PendingSignup savedSignup() {
        ArgumentCaptor<PendingSignup> saved = ArgumentCaptor.forClass(PendingSignup.class);
        verify(pendingSignups).saveUnlessRecent(saved.capture(), any());
        return saved.getValue();
    }

    @Test
    void a_pending_signup_is_recorded_with_a_hashed_code_and_an_expiry_and_no_account_is_saved() {
        service().create(newcomer());

        PendingSignup signup = savedSignup();
        Assertions.assertEquals("test@test.fr", signup.getEmail());
        Assertions.assertEquals("usertest", signup.getPseudo());
        Assertions.assertEquals("hashed", signup.getPasswordHash());
        Assertions.assertEquals(NOW, signup.getCreatedAt());
        Assertions.assertEquals(NOW.plus(TTL), signup.getExpiresAt());
        verify(accounts, never()).save(any());
        verify(emailingSpi, never()).sendSignupAttemptEmail(any());
    }

    @Test
    void only_the_hash_of_the_code_is_kept_and_the_clear_code_goes_in_the_mail() {
        service().create(newcomer());

        PendingSignup signup = savedSignup();
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailingSpi).sendSignupConfirmationEmail(any(), code.capture());
        Assertions.assertEquals(Account.VERIFICATION_CODE_LENGTH, code.getValue().length());
        Assertions.assertNotEquals(code.getValue(), signup.getCodeHash());
        Assertions.assertEquals(PendingSignup.hashOf(code.getValue()), signup.getCodeHash());
        Assertions.assertEquals(64, signup.getCodeHash().length());
    }

    @Test
    void the_replacement_is_only_allowed_once_the_cooldown_has_passed() {
        service().create(newcomer());

        ArgumentCaptor<Instant> recentSince = ArgumentCaptor.forClass(Instant.class);
        verify(pendingSignups).saveUnlessRecent(any(), recentSince.capture());
        Assertions.assertEquals(NOW.minus(COOLDOWN), recentSince.getValue());
    }

    @Test
    void a_recent_pending_signup_is_kept_and_nothing_is_mailed() {
        when(pendingSignups.saveUnlessRecent(any(), any())).thenReturn(false);

        Assertions.assertDoesNotThrow(() -> service().create(newcomer()));

        verify(emailingSpi, never()).sendSignupConfirmationEmail(any(), any());
    }

    @Test
    void a_mail_that_cannot_be_sent_is_not_reported_to_the_caller() {
        doThrow(new EmailNotSendException()).when(emailingSpi).sendSignupConfirmationEmail(any(), any());

        Assertions.assertDoesNotThrow(() -> service().create(newcomer()));
    }

    @Test
    void an_address_already_registered_records_nothing_and_its_owner_is_told_instead() {
        Account owner = owner();
        when(accounts.findByEmail("test@test.fr")).thenReturn(Optional.of(owner));
        when(noticeLog.claim(any(), any(), any())).thenReturn(true);

        Assertions.assertDoesNotThrow(() -> service().create(newcomer()));

        verify(pendingSignups, never()).saveUnlessRecent(any(), any());
        verify(emailingSpi, never()).sendSignupConfirmationEmail(any(), any());
        verify(emailingSpi).sendSignupAttemptEmail(owner);
    }

    @Test
    void the_notice_is_claimed_for_the_address_over_the_configured_window() {
        when(accounts.findByEmail("test@test.fr")).thenReturn(Optional.of(owner()));

        service().create(newcomer());

        verify(noticeLog).claim("test@test.fr", NOW, NOW.minus(NOTICE_WINDOW));
    }

    @Test
    void no_second_notice_is_sent_inside_the_window_and_the_caller_notices_nothing() {
        when(accounts.findByEmail("test@test.fr")).thenReturn(Optional.of(owner()));
        when(noticeLog.claim(any(), any(), any())).thenReturn(false);

        Assertions.assertDoesNotThrow(() -> service().create(newcomer()));

        verify(emailingSpi, never()).sendSignupAttemptEmail(any());
        verify(pendingSignups, never()).saveUnlessRecent(any(), any());
    }

    @Test
    void a_taken_pseudo_is_refused_before_the_address_is_looked_at_and_nobody_is_mailed() {
        Account account = newcomer();
        doThrow(new PseudoAlreadyUsedException()).when(checker).pseudoIsAvailable(account);

        Assertions.assertThrows(PseudoAlreadyUsedException.class, () -> service().create(account));

        verify(accounts, never()).findByEmail(any());
        verify(pendingSignups, never()).saveUnlessRecent(any(), any());
        verify(noticeLog, never()).claim(any(), any(), any());
        verify(emailingSpi, never()).sendSignupAttemptEmail(any());
        verify(emailingSpi, never()).sendSignupConfirmationEmail(any(), any());
    }

    @Test
    void the_password_is_hashed_whether_or_not_the_address_is_taken() {
        when(accounts.findByEmail("test@test.fr")).thenReturn(Optional.of(owner()));

        service().create(newcomer());

        verify(passwordHasher).hash("clear");
    }

    @Test
    void a_notice_that_cannot_be_sent_is_not_reported_to_the_caller() {
        when(accounts.findByEmail("test@test.fr")).thenReturn(Optional.of(owner()));
        when(noticeLog.claim(any(), any(), any())).thenReturn(true);
        doThrow(new EmailNotSendException()).when(emailingSpi).sendSignupAttemptEmail(any());

        Assertions.assertDoesNotThrow(() -> service().create(newcomer()));
    }

    @Test
    void the_hash_of_a_code_is_the_sha_256_in_hexadecimal() {
        Assertions.assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", PendingSignup.hashOf("abc"));
    }
}
