package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.account.EmailAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.PseudoAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.TokenNotValidException;
import fr.barlords.dndgateapi.domain.functional.exception.account.WrongPasswordException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountUpdaterServiceTest {
    private static final ClientAddress CLIENT = new ClientAddress("203.0.113.7");


    @Mock
    private AccountPersistenceSpi spi;

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

    @Mock
    private EmailingSpi emailingSpi;

    private AccountUpdaterService service;

    private final Account existing = Account.builder().pseudo("usertest").email("test@test.fr").password("oldhash").build();

    @BeforeEach
    void setUp() {
        service = new AccountUpdaterService(spi, currentAccountService, accountCheckerService,
                new AccountModifierService(passwordHasher), passwordVerifier, sessionRevoker,
                emailingSpi, Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC), Duration.ofHours(24));
        lenient().when(currentAccountService.getAccountById(existing.getId())).thenReturn(existing);
        lenient().when(passwordVerifier.verify(any(), any(), any(), any())).thenAnswer(i -> i.getArguments()[0]);
        lenient().when(spi.save(any(Account.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    @Test
    void should_not_update_an_account_that_does_not_exist() {
        UUID accountId = UUID.randomUUID();
        when(currentAccountService.getAccountById(accountId)).thenThrow(new TokenNotValidException());

        Assertions.assertThrows(TokenNotValidException.class,
                () -> service.update(accountId, "password", new AccountChanges("new@test.fr", null, null), CLIENT));
        verify(spi, never()).save(any());
    }

    // ---- the current password is required ----

    @Test
    void a_wrong_current_password_changes_nothing() {
        when(passwordVerifier.verify(any(), any(), any(), any())).thenThrow(new WrongPasswordException());

        Assertions.assertThrows(WrongPasswordException.class,
                () -> service.update(existing.getId(), "wrong", new AccountChanges("new@test.fr", "new", "newpassword"), CLIENT));

        verify(spi, never()).save(any());
        verify(sessionRevoker, never()).logoutAll(any());
    }

    @Test
    void the_current_password_is_checked_with_a_wrong_password_error() {
        service.update(existing.getId(), "oldpassword", new AccountChanges(null, "new", null), CLIENT);

        verify(passwordVerifier).verify(org.mockito.ArgumentMatchers.eq(existing), org.mockito.ArgumentMatchers.eq("oldpassword"), org.mockito.ArgumentMatchers.eq(CLIENT), any());
    }

    // ---- partial update ----

    @Test
    void only_the_given_fields_change() {
        Account updated = service.update(existing.getId(), "oldpassword", new AccountChanges(null, "newpseudo", null), CLIENT);

        Assertions.assertEquals("newpseudo", updated.getPseudo());
        Assertions.assertEquals("test@test.fr", updated.getEmail());
        Assertions.assertEquals("oldhash", updated.getPassword());
        verify(sessionRevoker, never()).logoutAll(any());
    }

    @Test
    void keeping_the_same_email_and_pseudo_is_not_a_conflict_with_oneself() {
        Assertions.assertDoesNotThrow(() -> service.update(existing.getId(), "oldpassword",
                new AccountChanges("test@test.fr", "usertest", null), CLIENT));

        // the account would find itself as the owner of that e-mail and pseudo: nothing is looked up
        verify(accountCheckerService, never()).emailIsAvailable(any());
        verify(accountCheckerService, never()).pseudoIsAvailable(any());
    }

    @Test
    void a_new_email_must_be_available() {
        doThrow(new EmailAlreadyUsedException()).when(accountCheckerService).emailIsAvailable(existing.withEmail("taken@test.fr"));

        Assertions.assertThrows(EmailAlreadyUsedException.class,
                () -> service.update(existing.getId(), "oldpassword", new AccountChanges("taken@test.fr", null, null), CLIENT));
        verify(spi, never()).save(any());
    }

    @Test
    void a_new_pseudo_must_be_available() {
        doThrow(new PseudoAlreadyUsedException()).when(accountCheckerService).pseudoIsAvailable(existing.withPseudo("taken"));

        Assertions.assertThrows(PseudoAlreadyUsedException.class,
                () -> service.update(existing.getId(), "oldpassword", new AccountChanges(null, "taken", null), CLIENT));
        verify(spi, never()).save(any());
    }

    // ---- the password ----

    @Test
    void a_new_password_is_hashed_and_ends_every_session() {
        when(passwordHasher.hash("newpassword")).thenReturn("newhash");

        Account updated = service.update(existing.getId(), "oldpassword", new AccountChanges(null, null, "newpassword"), CLIENT);

        Assertions.assertEquals("newhash", updated.getPassword());
        verify(sessionRevoker).logoutAll(existing.getId());
    }

    @Test
    void sending_the_current_password_as_the_new_one_changes_nothing_and_keeps_the_sessions() {
        Account updated = service.update(existing.getId(), "oldpassword", new AccountChanges(null, null, "oldpassword"), CLIENT);

        Assertions.assertEquals("oldhash", updated.getPassword());
        verify(sessionRevoker, never()).logoutAll(any());
        verify(passwordHasher, never()).hash(any());
    }
}
