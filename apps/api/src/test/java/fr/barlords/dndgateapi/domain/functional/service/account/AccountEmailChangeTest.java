package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.EmailNotSendException;
import fr.barlords.dndgateapi.domain.functional.exception.account.EmailAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.InvalidVerificationCodeException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;
import fr.barlords.dndgateapi.domain.functional.model.account.AccountChanges;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.account.AccountSessionRevokerApi;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Changing the e-mail address of an account: the new address only replaces the old one once its owner followed the
 * link sent to it. Until then the old address stays the one that identifies the account.
 */
@ExtendWith(MockitoExtension.class)
public class AccountEmailChangeTest {
    private static final ClientAddress CLIENT = new ClientAddress("203.0.113.7");


    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static final Duration TTL = Duration.ofHours(24);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private AccountPersistenceSpi spi;

    @Mock
    private EmailingSpi emailingSpi;

    private final Account existing = Account.builder().pseudo("usertest").email("old@test.fr").password("hash")
            .activated(true).build();

    private Account waitingFor(String newEmail, String code, Instant expiresAt) {
        return existing.withPendingEmail(newEmail).withPendingEmailCode(code).withPendingEmailExpiresAt(expiresAt);
    }

    // ================= the code itself =================

    @Nested
    class TheCode {

        @Test
        void the_right_code_works_until_it_expires() {
            Account account = waitingFor("new@test.fr", "secretcode", NOW.plusSeconds(60));

            Assertions.assertTrue(account.hasValidPendingEmailCode("secretcode", NOW));
            Assertions.assertFalse(account.hasValidPendingEmailCode("secretcode", NOW.plusSeconds(60)), "expired at the exact instant");
        }

        @Test
        void a_wrong_or_missing_code_never_works() {
            Account account = waitingFor("new@test.fr", "secretcode", NOW.plusSeconds(60));

            Assertions.assertFalse(account.hasValidPendingEmailCode("secretcodf", NOW));
            Assertions.assertFalse(account.hasValidPendingEmailCode("", NOW));
            Assertions.assertFalse(account.hasValidPendingEmailCode(null, NOW));
        }

        @Test
        void an_account_with_no_change_waiting_has_no_valid_code() {
            Assertions.assertFalse(existing.hasValidPendingEmailCode("anything", NOW));
            Assertions.assertFalse(existing.hasValidPendingEmailCode(null, NOW));
        }

        @Test
        void a_code_without_expiry_or_without_address_is_not_trusted() {
            Assertions.assertFalse(waitingFor("new@test.fr", "secretcode", null).hasValidPendingEmailCode("secretcode", NOW));
            Assertions.assertFalse(waitingFor(null, "secretcode", NOW.plusSeconds(60)).hasValidPendingEmailCode("secretcode", NOW));
        }
    }

    // ================= asking for the change =================

    @Nested
    class Requesting {

        @Mock
        private CurrentAccountService currentAccountService;

        @Mock
        private AccountCheckerService accountCheckerService;

        @Mock
        private PasswordHasherSpi passwordHasher;

        @Mock
        private AccountPasswordVerifierService passwordVerifier;

        @Mock
        private AccountSessionRevokerApi sessionRevoker;

        private AccountUpdaterService service;

        @BeforeEach
        void setUp() {
            service = new AccountUpdaterService(spi, currentAccountService, accountCheckerService,
                    new AccountModifierService(passwordHasher), passwordVerifier, sessionRevoker, emailingSpi, clock, TTL);
            lenient().when(currentAccountService.getAccountById(existing.getId())).thenReturn(existing);
            lenient().when(passwordVerifier.verify(any(), any(), any(), any())).thenAnswer(i -> i.getArguments()[0]);
            lenient().when(spi.save(any(Account.class))).thenAnswer(i -> i.getArguments()[0]);
        }

        @Test
        void a_new_address_waits_for_its_confirmation_and_the_account_keeps_the_old_one() {
            Account updated = service.update(existing.getId(), "password", new AccountChanges("new@test.fr", null, null), CLIENT);

            Assertions.assertEquals("old@test.fr", updated.getEmail());
            Assertions.assertEquals("new@test.fr", updated.getPendingEmail());
            Assertions.assertEquals(Account.VERIFICATION_CODE_LENGTH, updated.getPendingEmailCode().length());
            Assertions.assertEquals(NOW.plus(TTL), updated.getPendingEmailExpiresAt());
            Assertions.assertTrue(updated.isActivated(), "the account stays usable meanwhile");
        }

        @Test
        void the_link_is_mailed_to_the_new_address_with_the_code() {
            service.update(existing.getId(), "password", new AccountChanges("new@test.fr", null, null), CLIENT);

            ArgumentCaptor<Account> mailed = ArgumentCaptor.forClass(Account.class);
            verify(emailingSpi).sendEmailChangeEmail(mailed.capture());
            Assertions.assertEquals("new@test.fr", mailed.getValue().getPendingEmail());
            Assertions.assertNotNull(mailed.getValue().getPendingEmailCode());
        }

        @Test
        void the_new_address_must_be_available() {
            doThrow(new EmailAlreadyUsedException()).when(accountCheckerService).emailIsAvailable(existing.withEmail("taken@test.fr"));

            Assertions.assertThrows(EmailAlreadyUsedException.class,
                    () -> service.update(existing.getId(), "password", new AccountChanges("taken@test.fr", null, null), CLIENT));

            verify(spi, never()).save(any());
            verify(emailingSpi, never()).sendEmailChangeEmail(any());
        }

        @Test
        void asking_again_replaces_the_previous_request() {
            Account first = service.update(existing.getId(), "password", new AccountChanges("first@test.fr", null, null), CLIENT);
            when(currentAccountService.getAccountById(existing.getId())).thenReturn(first);

            Account second = service.update(existing.getId(), "password", new AccountChanges("second@test.fr", null, null), CLIENT);

            Assertions.assertEquals("second@test.fr", second.getPendingEmail());
            Assertions.assertNotEquals(first.getPendingEmailCode(), second.getPendingEmailCode());
            Assertions.assertFalse(second.hasValidPendingEmailCode(first.getPendingEmailCode(), NOW));
        }

        @Test
        void keeping_the_current_address_asks_for_nothing_and_cancels_nothing() {
            Account waiting = waitingFor("new@test.fr", "secretcode", NOW.plus(TTL));
            when(currentAccountService.getAccountById(existing.getId())).thenReturn(waiting);

            Account updated = service.update(existing.getId(), "password", new AccountChanges("old@test.fr", null, null), CLIENT);

            Assertions.assertEquals("new@test.fr", updated.getPendingEmail());
            verify(emailingSpi, never()).sendEmailChangeEmail(any());
        }

        @Test
        void the_other_changes_of_the_same_request_apply_at_once() {
            Account updated = service.update(existing.getId(), "password", new AccountChanges("new@test.fr", "newpseudo", null), CLIENT);

            Assertions.assertEquals("newpseudo", updated.getPseudo());
            Assertions.assertEquals("old@test.fr", updated.getEmail());
        }

        @Test
        void a_mail_that_cannot_be_sent_does_not_fail_the_update() {
            doThrow(new EmailNotSendException(new RuntimeException())).when(emailingSpi).sendEmailChangeEmail(any());

            Account updated = Assertions.assertDoesNotThrow(
                    () -> service.update(existing.getId(), "password", new AccountChanges("new@test.fr", null, null), CLIENT));

            Assertions.assertEquals("new@test.fr", updated.getPendingEmail());
        }
    }

    // ================= confirming the change =================

    @Nested
    class Confirming {

        private AccountEmailChangeConfirmerService service() {
            return new AccountEmailChangeConfirmerService(spi, new AccountCheckerService(spi), clock);
        }

        @Test
        void the_right_code_replaces_the_address_and_is_used_up() {
            Account waiting = waitingFor("new@test.fr", "secretcode", NOW.plus(TTL));
            when(spi.findAllByPendingEmail("new@test.fr")).thenReturn(List.of(waiting));
            when(spi.findByEmail("new@test.fr")).thenReturn(Optional.empty());
            when(spi.save(any(Account.class))).thenAnswer(i -> i.getArguments()[0]);

            service().confirm("new@test.fr", "secretcode");

            ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
            verify(spi).save(saved.capture());
            Assertions.assertEquals("new@test.fr", saved.getValue().getEmail());
            Assertions.assertNull(saved.getValue().getPendingEmail());
            Assertions.assertNull(saved.getValue().getPendingEmailCode());
            Assertions.assertNull(saved.getValue().getPendingEmailExpiresAt());
        }

        @Test
        void two_accounts_waiting_for_the_same_address_do_not_block_each_other() {
            // nothing reserves an address before it is confirmed: someone else's request must not decide who gets it
            Account stranger = Account.builder().pseudo("saruman").email("saruman@test.fr").password("hash")
                    .pendingEmail("new@test.fr").pendingEmailCode("strangercode").pendingEmailExpiresAt(NOW.plus(TTL)).build();
            Account owner = waitingFor("new@test.fr", "secretcode", NOW.plus(TTL));
            when(spi.findAllByPendingEmail("new@test.fr")).thenReturn(List.of(stranger, owner));
            when(spi.findByEmail("new@test.fr")).thenReturn(Optional.empty());
            when(spi.save(any(Account.class))).thenAnswer(i -> i.getArguments()[0]);

            service().confirm("new@test.fr", "secretcode");

            ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
            verify(spi).save(saved.capture());
            Assertions.assertEquals(owner.getId(), saved.getValue().getId());
        }

        @Test
        void a_wrong_code_changes_nothing() {
            when(spi.findAllByPendingEmail("new@test.fr")).thenReturn(List.of(waitingFor("new@test.fr", "secretcode", NOW.plus(TTL))));

            Assertions.assertThrows(InvalidVerificationCodeException.class, () -> service().confirm("new@test.fr", "wrongcode"));
            verify(spi, never()).save(any());
        }

        @Test
        void an_expired_code_changes_nothing() {
            when(spi.findAllByPendingEmail("new@test.fr")).thenReturn(List.of(waitingFor("new@test.fr", "secretcode", NOW.minusSeconds(1))));

            Assertions.assertThrows(InvalidVerificationCodeException.class, () -> service().confirm("new@test.fr", "secretcode"));
            verify(spi, never()).save(any());
        }

        @Test
        void an_address_nobody_is_waiting_for_gets_the_same_error_as_a_wrong_code() {
            when(spi.findAllByPendingEmail("nobody@test.fr")).thenReturn(List.of());

            InvalidVerificationCodeException unknown = Assertions.assertThrows(InvalidVerificationCodeException.class,
                    () -> service().confirm("nobody@test.fr", "secretcode"));

            Assertions.assertEquals(InvalidVerificationCodeException.EXCEPTION_MESSAGE, unknown.getMessage());
        }

        @Test
        void an_address_taken_in_the_meantime_is_refused_and_the_old_address_stays() {
            when(spi.findAllByPendingEmail("new@test.fr")).thenReturn(List.of(waitingFor("new@test.fr", "secretcode", NOW.plus(TTL))));
            when(spi.findByEmail("new@test.fr")).thenReturn(Optional.of(Account.builder().email("new@test.fr").build()));

            Assertions.assertThrows(EmailAlreadyUsedException.class, () -> service().confirm("new@test.fr", "secretcode"));
            verify(spi, never()).save(any());
        }
    }
}
