package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.EmailNotSendException;
import fr.barlords.dndgateapi.domain.functional.exception.account.InvalidVerificationCodeException;
import fr.barlords.dndgateapi.domain.functional.exception.account.PseudoAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import fr.barlords.dndgateapi.domain.port.out.PasswordHasherSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
 * The life of the verification code of an e-mail address: issued at registration, valid for a while, used once,
 * renewed on request.
 */
@ExtendWith(MockitoExtension.class)
public class AccountVerificationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static final Duration TTL = Duration.ofHours(24);

    private static final Duration COOLDOWN = Duration.ofMinutes(1);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private AccountPersistenceSpi spi;

    @Mock
    private EmailingSpi emailingSpi;

    private Account pending(String code, Instant expiresAt) {
        return Account.builder().pseudo("usertest").email("test@test.fr").password("hash")
                .verificationCode(code).verificationExpiresAt(expiresAt).build();
    }

    // ================= the code itself =================

    @Nested
    class TheCode {

        @Test
        void the_right_code_works_until_it_expires() {
            Account account = pending("secretcode", NOW.plusSeconds(60));

            Assertions.assertTrue(account.hasValidVerificationCode("secretcode", NOW));
            Assertions.assertFalse(account.hasValidVerificationCode("secretcode", NOW.plusSeconds(60)), "expired at the exact instant");
            Assertions.assertFalse(account.hasValidVerificationCode("secretcode", NOW.plusSeconds(3600)));
        }

        @Test
        void a_wrong_or_missing_code_never_works() {
            Account account = pending("secretcode", NOW.plusSeconds(60));

            Assertions.assertFalse(account.hasValidVerificationCode("secretcodf", NOW));
            Assertions.assertFalse(account.hasValidVerificationCode("SECRETCODE", NOW));
            Assertions.assertFalse(account.hasValidVerificationCode("secret", NOW));
            Assertions.assertFalse(account.hasValidVerificationCode("secretcode ", NOW));
            Assertions.assertFalse(account.hasValidVerificationCode("", NOW));
            Assertions.assertFalse(account.hasValidVerificationCode(null, NOW));
        }

        @Test
        void an_account_without_code_or_without_expiry_cannot_be_verified() {
            Assertions.assertFalse(pending(null, NOW.plusSeconds(60)).hasValidVerificationCode("anything", NOW));
            Assertions.assertFalse(pending(null, NOW.plusSeconds(60)).hasValidVerificationCode(null, NOW));
            // a code issued before the expiry date existed is not trusted
            Assertions.assertFalse(pending("secretcode", null).hasValidVerificationCode("secretcode", NOW));
        }

        @Test
        void a_new_account_gets_a_long_random_code() {
            Account first = Account.builder().build();
            Account second = Account.builder().build();

            Assertions.assertEquals(Account.VERIFICATION_CODE_LENGTH, first.getVerificationCode().length());
            Assertions.assertTrue(first.getVerificationCode().matches("[0-9A-Za-z]+"));
            Assertions.assertNotEquals(first.getVerificationCode(), second.getVerificationCode());
        }
    }

    // ================= verifying =================

    @Nested
    class Verifying {

        private AccountVerifierService service() {
            return new AccountVerifierService(spi, clock);
        }

        @Test
        void the_right_code_activates_the_account_and_is_used_up() {
            Account account = pending("secretcode", NOW.plus(TTL));
            when(spi.findByEmail("test@test.fr")).thenReturn(Optional.of(account));
            when(spi.save(any(Account.class))).thenAnswer(i -> i.getArguments()[0]);

            service().verify("test@test.fr", "secretcode");

            ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
            verify(spi).save(saved.capture());
            Assertions.assertTrue(saved.getValue().isActivated());
            Assertions.assertNull(saved.getValue().getVerificationCode());
            Assertions.assertNull(saved.getValue().getVerificationExpiresAt());
        }

        @Test
        void a_wrong_code_activates_nothing() {
            when(spi.findByEmail("test@test.fr")).thenReturn(Optional.of(pending("secretcode", NOW.plus(TTL))));

            Assertions.assertThrows(InvalidVerificationCodeException.class, () -> service().verify("test@test.fr", "wrongcode"));
            verify(spi, never()).save(any());
        }

        @Test
        void an_expired_code_activates_nothing() {
            when(spi.findByEmail("test@test.fr")).thenReturn(Optional.of(pending("secretcode", NOW.minusSeconds(1))));

            Assertions.assertThrows(InvalidVerificationCodeException.class, () -> service().verify("test@test.fr", "secretcode"));
            verify(spi, never()).save(any());
        }

        @Test
        void a_code_cannot_be_used_twice() {
            Account verified = pending(null, null).withActivated(true);
            when(spi.findByEmail("test@test.fr")).thenReturn(Optional.of(verified));

            Assertions.assertThrows(InvalidVerificationCodeException.class, () -> service().verify("test@test.fr", "secretcode"));
        }

        @Test
        void an_unknown_address_gets_the_same_error_as_a_wrong_code() {
            when(spi.findByEmail("nobody@test.fr")).thenReturn(Optional.empty());

            InvalidVerificationCodeException unknown = Assertions.assertThrows(InvalidVerificationCodeException.class,
                    () -> service().verify("nobody@test.fr", "secretcode"));

            Assertions.assertEquals(InvalidVerificationCodeException.EXCEPTION_MESSAGE, unknown.getMessage());
        }
    }

    // ================= asking for a new mail =================

    @Nested
    class Resending {

        private AccountVerificationResenderService service() {
            return new AccountVerificationResenderService(spi, emailingSpi, clock, TTL, COOLDOWN);
        }

        private void saveReturnsWhatItReceives() {
            lenient().when(spi.save(any(Account.class))).thenAnswer(i -> i.getArguments()[0]);
        }

        /** An account whose last mail was sent this long ago. */
        private Account sentAgo(Duration ago) {
            return pending("oldcode", NOW.minus(ago).plus(TTL));
        }

        @Test
        void a_new_code_replaces_the_old_one_and_is_mailed() {
            Account account = sentAgo(Duration.ofHours(2));
            when(spi.findByEmail("test@test.fr")).thenReturn(Optional.of(account));
            saveReturnsWhatItReceives();

            service().resend("test@test.fr");

            ArgumentCaptor<Account> mailed = ArgumentCaptor.forClass(Account.class);
            verify(emailingSpi).sendAccountValidationEmail(mailed.capture());
            Assertions.assertNotEquals("oldcode", mailed.getValue().getVerificationCode());
            Assertions.assertEquals(Account.VERIFICATION_CODE_LENGTH, mailed.getValue().getVerificationCode().length());
            Assertions.assertEquals(NOW.plus(TTL), mailed.getValue().getVerificationExpiresAt());
            Assertions.assertFalse(mailed.getValue().hasValidVerificationCode("oldcode", NOW));
        }

        @Test
        void an_expired_code_can_be_replaced() {
            when(spi.findByEmail("test@test.fr")).thenReturn(Optional.of(pending("oldcode", NOW.minus(Duration.ofDays(3)))));
            saveReturnsWhatItReceives();

            service().resend("test@test.fr");

            verify(emailingSpi).sendAccountValidationEmail(any());
        }

        @Test
        void an_account_from_before_the_expiry_existed_can_get_a_new_code() {
            when(spi.findByEmail("test@test.fr")).thenReturn(Optional.of(pending("oldcode", null)));
            saveReturnsWhatItReceives();

            service().resend("test@test.fr");

            verify(emailingSpi).sendAccountValidationEmail(any());
        }

        @Test
        void nothing_is_sent_to_an_unknown_address() {
            when(spi.findByEmail("nobody@test.fr")).thenReturn(Optional.empty());

            Assertions.assertDoesNotThrow(() -> service().resend("nobody@test.fr"));
            verify(emailingSpi, never()).sendAccountValidationEmail(any());
        }

        @Test
        void nothing_is_sent_to_an_address_that_is_already_verified() {
            when(spi.findByEmail("test@test.fr")).thenReturn(Optional.of(pending(null, null).withActivated(true)));

            service().resend("test@test.fr");

            verify(emailingSpi, never()).sendAccountValidationEmail(any());
            verify(spi, never()).save(any());
        }

        @Test
        void nothing_is_sent_to_an_anonymized_account() {
            when(spi.findByEmail("test@test.fr")).thenReturn(Optional.of(pending("oldcode", NOW).withAnonymizedAt(NOW)));

            service().resend("test@test.fr");

            verify(emailingSpi, never()).sendAccountValidationEmail(any());
        }

        @Test
        void a_second_request_right_after_the_first_sends_nothing() {
            when(spi.findByEmail("test@test.fr")).thenReturn(Optional.of(sentAgo(Duration.ofSeconds(10))));

            service().resend("test@test.fr");

            verify(emailingSpi, never()).sendAccountValidationEmail(any());
            verify(spi, never()).save(any());
        }

        @Test
        void a_request_after_the_cooldown_sends_again() {
            when(spi.findByEmail("test@test.fr")).thenReturn(Optional.of(sentAgo(COOLDOWN.plusSeconds(1))));
            saveReturnsWhatItReceives();

            service().resend("test@test.fr");

            verify(emailingSpi).sendAccountValidationEmail(any());
        }

        @Test
        void a_mail_that_cannot_be_sent_is_not_reported_to_the_caller() {
            when(spi.findByEmail("test@test.fr")).thenReturn(Optional.of(sentAgo(Duration.ofHours(2))));
            saveReturnsWhatItReceives();
            doThrow(new EmailNotSendException()).when(emailingSpi).sendAccountValidationEmail(any());

            Assertions.assertDoesNotThrow(() -> service().resend("test@test.fr"));
        }
    }

    // ================= registration =================

    @Nested
    class Registering {

        @Mock
        private AccountCheckerService checker;

        @Mock
        private PasswordHasherSpi passwordHasher;

        private AccountCreatorService service() {
            return new AccountCreatorService(spi, checker, new AccountModifierService(passwordHasher), emailingSpi, clock, TTL);
        }

        @BeforeEach
        void setUp() {
            lenient().when(passwordHasher.hash(any())).thenReturn("hashed");
            lenient().when(spi.save(any(Account.class))).thenAnswer(i -> i.getArguments()[0]);
        }

        private Account savedAccount() {
            ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
            verify(spi).save(saved.capture());
            return saved.getValue();
        }

        private Account newcomer() {
            return Account.builder().pseudo("usertest").email("test@test.fr").password("clear").build();
        }

        @Test
        void the_account_is_saved_with_a_fresh_code_that_expires_and_the_mail_is_sent() {
            service().create(newcomer());

            Account created = savedAccount();
            Assertions.assertEquals("hashed", created.getPassword());
            Assertions.assertFalse(created.isActivated());
            Assertions.assertEquals(Account.VERIFICATION_CODE_LENGTH, created.getVerificationCode().length());
            Assertions.assertEquals(NOW.plus(TTL), created.getVerificationExpiresAt());
            verify(emailingSpi).sendAccountValidationEmail(created);
            verify(emailingSpi, never()).sendSignupAttemptEmail(any());
        }

        @Test
        void the_code_does_not_depend_on_what_the_caller_put_in_the_account() {
            service().create(Account.builder().pseudo("usertest").email("test@test.fr").password("clear")
                    .verificationCode("chosen-by-the-caller").verificationExpiresAt(NOW.plus(Duration.ofDays(3650))).build());

            Account created = savedAccount();
            Assertions.assertNotEquals("chosen-by-the-caller", created.getVerificationCode());
            Assertions.assertEquals(NOW.plus(TTL), created.getVerificationExpiresAt());
        }

        @Test
        void a_mail_that_cannot_be_sent_does_not_lock_the_account_out() {
            doThrow(new EmailNotSendException()).when(emailingSpi).sendAccountValidationEmail(any());

            Assertions.assertDoesNotThrow(() -> service().create(newcomer()));

            verify(spi).save(any(Account.class));
        }

        @Test
        void an_address_already_registered_changes_nothing_and_its_owner_is_told_instead() {
            Account owner = Account.builder().pseudo("owner").email("test@test.fr").password("hash").activated(true).build();
            when(spi.findByEmail("test@test.fr")).thenReturn(Optional.of(owner));

            Assertions.assertDoesNotThrow(() -> service().create(newcomer()));

            verify(spi, never()).save(any());
            verify(emailingSpi, never()).sendAccountValidationEmail(any());
            verify(emailingSpi).sendSignupAttemptEmail(owner);
        }

        @Test
        void the_password_is_hashed_whether_or_not_the_address_is_taken() {
            when(spi.findByEmail("test@test.fr")).thenReturn(Optional.of(
                    Account.builder().pseudo("owner").email("test@test.fr").password("hash").build()));

            service().create(newcomer());

            verify(passwordHasher).hash("clear");
        }

        @Test
        void a_notice_that_cannot_be_sent_is_not_reported_to_the_caller() {
            when(spi.findByEmail("test@test.fr")).thenReturn(Optional.of(
                    Account.builder().pseudo("owner").email("test@test.fr").password("hash").build()));
            doThrow(new EmailNotSendException()).when(emailingSpi).sendSignupAttemptEmail(any());

            Assertions.assertDoesNotThrow(() -> service().create(newcomer()));
        }

        @Test
        void nothing_is_saved_nor_mailed_when_the_pseudo_is_taken() {
            Account account = Account.builder().pseudo("usertest").email("test@test.fr").password("clear").build();
            doThrow(new PseudoAlreadyUsedException()).when(checker).pseudoIsAvailable(account);

            Assertions.assertThrows(PseudoAlreadyUsedException.class, () -> service().create(account));

            verify(spi, never()).save(any());
            verify(emailingSpi, never()).sendAccountValidationEmail(any());
        }
    }
}
