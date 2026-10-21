package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.account.AccountNotValidatedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.EmailAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.PseudoAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountCheckerServiceTest {

    @InjectMocks
    private AccountCheckerService service;

    @Mock
    private AccountPersistenceSpi spi;

    @Test
    void should_throw_error_because_pseudo_is_not_available() {
        val pseudo = "accounttest";
        val email = "test@test.fr";
        val password = "accountpassword";
        val given = Account.builder()
                .pseudo(pseudo)
                .email(email)
                .password(password)
                .build();

        when(spi.findByPseudo(given.getPseudo())).thenReturn(Optional.of(given));

        Assertions.assertThrows(PseudoAlreadyUsedException.class, () -> service.pseudoIsAvailable(given));
        verifyNoMoreInteractions(spi);
    }

    @Test
    void should_pseudo_is_available() {
        val pseudo = "accounttest";
        val email = "test@test.fr";
        val password = "accountpassword";
        val given = Account.builder()
                .pseudo(pseudo)
                .email(email)
                .password(password)
                .build();

        when(spi.findByPseudo(given.getPseudo())).thenReturn(Optional.empty());

        Assertions.assertDoesNotThrow(() -> service.pseudoIsAvailable(given));
        verifyNoMoreInteractions(spi);
    }

    @Test
    void should_throw_error_because_email_is_not_available() {
        val pseudo = "accounttest";
        val email = "test@test.fr";
        val password = "accountpassword";
        val given = Account.builder()
                .pseudo(pseudo)
                .email(email)
                .password(password)
                .build();

        when(spi.findByEmail(given.getEmail())).thenReturn(Optional.of(given));

        Assertions.assertThrows(EmailAlreadyUsedException.class, () -> service.emailIsAvailable(given));
        verifyNoMoreInteractions(spi);
    }

    @Test
    void should_email_is_available() {
        val pseudo = "accounttest";
        val email = "test@test.fr";
        val password = "accountpassword";
        val given = Account.builder()
                .pseudo(pseudo)
                .email(email)
                .password(password)
                .build();

        when(spi.findByEmail(given.getEmail())).thenReturn(Optional.empty());
        verifyNoMoreInteractions(spi);

        Assertions.assertDoesNotThrow(() -> service.emailIsAvailable(given));
    }

    @Test
    void should_throw_error_because_account_is_not_activated() {
        val pseudo = "accounttest";
        val email = "test@test.fr";
        val password = "accountpassword";
        val given = Account.builder()
                .pseudo(pseudo)
                .email(email)
                .password(password)
                .build();

        Assertions.assertThrows(AccountNotValidatedException.class, () -> service.accountIsActivated(given));
    }

    @Test
    void should_account_is_activated() {
        val pseudo = "accounttest";
        val email = "test@test.fr";
        val password = "accountpassword";
        val given = Account.builder()
                .pseudo(pseudo)
                .email(email)
                .password(password)
                .activated(true)
                .build();

        Assertions.assertDoesNotThrow(() -> service.accountIsActivated(given));
    }
}
