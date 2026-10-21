package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountFinderServiceTest {

    @InjectMocks
    private AccountFinderService service;

    @Mock
    private AccountPersistenceSpi spi;

    @Test
    void should_find_account_by_id() {
        val pseudo = "accounttest";
        val email = "test@test.fr";
        val password = "accountpassword";
        val given = Account.builder()
                .pseudo(pseudo)
                .email(email)
                .password(password)
                .build();

        when(spi.findById(given.getId())).thenReturn(Optional.of(given));
        verifyNoMoreInteractions(spi);

        val actual = service.findById(given.getId());

        Assertions.assertFalse(actual.isEmpty());
    }

    @Test
    void should_not_find_account_by_id() {
        val pseudo = "accounttest";
        val email = "test@test.fr";
        val password = "accountpassword";
        val given = Account.builder()
                .pseudo(pseudo)
                .email(email)
                .password(password)
                .build();

        when(spi.findById(given.getId())).thenReturn(Optional.empty());
        verifyNoMoreInteractions(spi);

        val actual = service.findById(given.getId());

        Assertions.assertTrue(actual.isEmpty());
    }

    @Test
    void should_find_account_by_email() {
        val pseudo = "accounttest";
        val email = "test@test.fr";
        val password = "accountpassword";
        val given = Account.builder()
                .pseudo(pseudo)
                .email(email)
                .password(password)
                .build();

        when(spi.findByEmail(given.getEmail())).thenReturn(Optional.of(given));
        verifyNoMoreInteractions(spi);

        val actual = service.findByEmail(given.getEmail());

        Assertions.assertFalse(actual.isEmpty());
    }

    @Test
    void should_not_find_account_by_email() {
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

        val actual = service.findByEmail(given.getEmail());

        Assertions.assertTrue(actual.isEmpty());
    }

    @Test
    void should_find_account_by_pseudo() {
        val pseudo = "accounttest";
        val email = "test@test.fr";
        val password = "accountpassword";
        val given = Account.builder()
                .pseudo(pseudo)
                .email(email)
                .password(password)
                .build();

        when(spi.findByPseudo(given.getPseudo())).thenReturn(Optional.of(given));
        verifyNoMoreInteractions(spi);

        val actual = service.findByPseudo(given.getPseudo());

        Assertions.assertFalse(actual.isEmpty());
    }

    @Test
    void should_not_find_account_by_pseudo() {
        val pseudo = "accounttest";
        val email = "test@test.fr";
        val password = "accountpassword";
        val given = Account.builder()
                .pseudo(pseudo)
                .email(email)
                .password(password)
                .build();

        when(spi.findByPseudo(given.getPseudo())).thenReturn(Optional.empty());
        verifyNoMoreInteractions(spi);

        val actual = service.findByPseudo(given.getPseudo());

        Assertions.assertTrue(actual.isEmpty());
    }

}
