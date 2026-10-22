package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.EmailNotSendException;
import fr.barlords.dndgateapi.domain.functional.exception.account.EmailAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.InvalidVerificationCodeException;
import fr.barlords.dndgateapi.domain.functional.exception.account.PseudoAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.PendingSignup;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import fr.barlords.dndgateapi.domain.port.out.PendingSignupPersistenceSpi;
import org.junit.jupiter.api.Assertions;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The life of the confirmation code of a pending signup: valid for a while, used once, renewed on request.
 * The account only exists once the code was given back.
 */
@ExtendWith(MockitoExtension.class)
public class AccountVerificationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static final Duration TTL = Duration.ofHours(24);

    private static final Duration COOLDOWN = Duration.ofMinutes(1);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private AccountPersistenceSpi accounts;

    @Mock
    private PendingSignupPersistenceSpi pendingSignups;

    @Mock
    private EmailingSpi emailingSpi;

    private PendingSignup signup(String code, Instant expiresAt) {
        return PendingSignup.builder().email("test@test.fr").pseudo("usertest").passwordHash("hash")
                .codeHash(PendingSignup.hashOf(code)).createdAt(NOW.minusSeconds(3600)).expiresAt(expiresAt).build();
    }

    // ================= the code itself =================

    @Nested
    class TheCode {

        @Test
        void the_right_code_works_until_it_expires() {
            PendingSignup signup = signup("secretcode", NOW.plusSeconds(60));

            Assertions.assertTrue(signup.hasValidCode("secretcode", NOW));
            Assertions.assertFalse(signup.hasValidCode("secretcode", NOW.plusSeconds(60)), "expired at the exact instant");
            Assertions.assertFalse(signup.hasValidCode("secretcode", NOW.plusSeconds(3600)));
        }

        @Test
        void a_wrong_or_missing_code_never_works() {
            PendingSignup signup = signup("secretcode", NOW.plusSeconds(60));

            Assertions.assertFalse(signup.hasValidCode("secretcodf", NOW));
            Assertions.assertFalse(signup.hasValidCode("SECRETCODE", NOW));
            Assertions.assertFalse(signup.hasValidCode("secret", NOW));
            Assertions.assertFalse(signup.hasValidCode("secretcode ", NOW));
            Assertions.assertFalse(signup.hasValidCode("", NOW));
            Assertions.assertFalse(signup.hasValidCode(null, NOW));
        }

        @Test
        void the_hash_that_is_stored_is_not_a_code_that_works() {
            PendingSignup signup = signup("secretcode", NOW.plusSeconds(60));

            Assertions.assertFalse(signup.hasValidCode(signup.getCodeHash(), NOW), "knowing the hash is not knowing the code");
        }
    }

    // ================= confirming =================

    @Nested
    class Confirming {

        private AccountVerifierService service() {
            return new AccountVerifierService(accounts, pendingSignups, clock);
        }

        private void thePendingSignupIs(PendingSignup signup) {
            when(pendingSignups.findByEmail("test@test.fr")).thenReturn(Optional.of(signup));
        }

        @Test
        void the_right_code_creates_the_account_already_activated_and_uses_the_signup_up() {
            PendingSignup signup = signup("secretcode", NOW.plus(TTL));
            thePendingSignupIs(signup);
            when(pendingSignups.deleteByEmailAndCodeHash("test@test.fr", signup.getCodeHash())).thenReturn(true);
            when(accounts.save(any(Account.class))).thenAnswer(i -> i.getArguments()[0]);

            service().verify("test@test.fr", "secretcode");

            ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
            verify(accounts).save(saved.capture());
            Assertions.assertTrue(saved.getValue().isActivated());
            Assertions.assertEquals("usertest", saved.getValue().getPseudo());
            Assertions.assertEquals("test@test.fr", saved.getValue().getEmail());
            Assertions.assertEquals("hash", saved.getValue().getPassword());
        }

        @Test
        void a_wrong_code_creates_nothing_and_keeps_the_signup() {
            thePendingSignupIs(signup("secretcode", NOW.plus(TTL)));

            Assertions.assertThrows(InvalidVerificationCodeException.class, () -> service().verify("test@test.fr", "wrongcode"));

            verify(accounts, never()).save(any());
            verify(pendingSignups, never()).deleteByEmailAndCodeHash(anyString(), anyString());
        }

        @Test
        void an_expired_code_creates_nothing() {
            thePendingSignupIs(signup("secretcode", NOW.minusSeconds(1)));

            Assertions.assertThrows(InvalidVerificationCodeException.class, () -> service().verify("test@test.fr", "secretcode"));

            verify(accounts, never()).save(any());
        }

        @Test
        void an_unknown_address_gets_the_same_error_as_a_wrong_code() {
            when(pendingSignups.findByEmail("nobody@test.fr")).thenReturn(Optional.empty());

            InvalidVerificationCodeException unknown = Assertions.assertThrows(InvalidVerificationCodeException.class,
                    () -> service().verify("nobody@test.fr", "secretcode"));

            Assertions.assertEquals(InvalidVerificationCodeException.EXCEPTION_MESSAGE, unknown.getMessage());
        }

        @Test
        void a_signup_somebody_else_used_up_in_the_meantime_creates_nothing() {
            PendingSignup signup = signup("secretcode", NOW.plus(TTL));
            thePendingSignupIs(signup);
            when(pendingSignups.deleteByEmailAndCodeHash("test@test.fr", signup.getCodeHash())).thenReturn(false);

            Assertions.assertThrows(InvalidVerificationCodeException.class, () -> service().verify("test@test.fr", "secretcode"));

            verify(accounts, never()).save(any());
        }

        @Test
        void a_pseudo_taken_in_the_meantime_is_refused() {
            PendingSignup signup = signup("secretcode", NOW.plus(TTL));
            thePendingSignupIs(signup);
            when(pendingSignups.deleteByEmailAndCodeHash("test@test.fr", signup.getCodeHash())).thenReturn(true);
            when(accounts.findByPseudo("usertest")).thenReturn(Optional.of(Account.builder().pseudo("usertest").build()));

            Assertions.assertThrows(PseudoAlreadyUsedException.class, () -> service().verify("test@test.fr", "secretcode"));

            verify(accounts, never()).save(any());
        }

        @Test
        void an_address_registered_in_the_meantime_is_refused() {
            PendingSignup signup = signup("secretcode", NOW.plus(TTL));
            thePendingSignupIs(signup);
            when(pendingSignups.deleteByEmailAndCodeHash("test@test.fr", signup.getCodeHash())).thenReturn(true);
            when(accounts.findByEmail("test@test.fr")).thenReturn(Optional.of(Account.builder().email("test@test.fr").build()));

            Assertions.assertThrows(EmailAlreadyUsedException.class, () -> service().verify("test@test.fr", "secretcode"));

            verify(accounts, never()).save(any());
        }

        @Test
        void discarding_forgets_the_signup_of_that_code_only() {
            service().discard("test@test.fr", "secretcode");

            verify(pendingSignups).deleteByEmailAndCodeHash("test@test.fr", PendingSignup.hashOf("secretcode"));
        }
    }

    // ================= asking for a new mail =================

    @Nested
    class Resending {

        private AccountVerificationResenderService service() {
            return new AccountVerificationResenderService(pendingSignups, emailingSpi, clock, TTL, COOLDOWN);
        }

        private void thePendingSignupIs(PendingSignup signup) {
            when(pendingSignups.findByEmail("test@test.fr")).thenReturn(Optional.of(signup));
        }

        @Test
        void a_new_code_replaces_the_old_one_and_is_mailed() {
            PendingSignup signup = signup("oldcode", NOW.plus(TTL));
            thePendingSignupIs(signup);
            when(pendingSignups.renewUnlessRecent(eq("test@test.fr"), anyString(), eq(NOW), eq(NOW.plus(TTL)), eq(NOW.minus(COOLDOWN))))
                    .thenReturn(true);

            service().resend("test@test.fr");

            ArgumentCaptor<String> storedHash = ArgumentCaptor.forClass(String.class);
            verify(pendingSignups).renewUnlessRecent(eq("test@test.fr"), storedHash.capture(), any(), any(), any());
            ArgumentCaptor<PendingSignup> mailedSignup = ArgumentCaptor.forClass(PendingSignup.class);
            ArgumentCaptor<String> mailedCode = ArgumentCaptor.forClass(String.class);
            verify(emailingSpi).sendSignupConfirmationEmail(mailedSignup.capture(), mailedCode.capture());
            Assertions.assertNotEquals("oldcode", mailedCode.getValue());
            Assertions.assertEquals(Account.VERIFICATION_CODE_LENGTH, mailedCode.getValue().length());
            Assertions.assertEquals(PendingSignup.hashOf(mailedCode.getValue()), storedHash.getValue(),
                    "the hash that is kept is the one of the code that is mailed");
            Assertions.assertEquals("usertest", mailedSignup.getValue().getPseudo());
            Assertions.assertEquals(NOW.plus(TTL), mailedSignup.getValue().getExpiresAt());
        }

        @Test
        void nothing_is_sent_to_an_unknown_address() {
            when(pendingSignups.findByEmail("nobody@test.fr")).thenReturn(Optional.empty());

            Assertions.assertDoesNotThrow(() -> service().resend("nobody@test.fr"));

            verify(emailingSpi, never()).sendSignupConfirmationEmail(any(), any());
            verify(pendingSignups, never()).renewUnlessRecent(any(), any(), any(), any(), any());
        }

        @Test
        void a_request_inside_the_cooldown_sends_nothing() {
            thePendingSignupIs(signup("oldcode", NOW.plus(TTL)));
            when(pendingSignups.renewUnlessRecent(any(), any(), any(), any(), any())).thenReturn(false);

            service().resend("test@test.fr");

            verify(emailingSpi, never()).sendSignupConfirmationEmail(any(), any());
        }

        @Test
        void a_mail_that_cannot_be_sent_is_not_reported_to_the_caller() {
            thePendingSignupIs(signup("oldcode", NOW.plus(TTL)));
            when(pendingSignups.renewUnlessRecent(any(), any(), any(), any(), any())).thenReturn(true);
            doThrow(new EmailNotSendException()).when(emailingSpi).sendSignupConfirmationEmail(any(), any());

            Assertions.assertDoesNotThrow(() -> service().resend("test@test.fr"));
        }
    }
}
