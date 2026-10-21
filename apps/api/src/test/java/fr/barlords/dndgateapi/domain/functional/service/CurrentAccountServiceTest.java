package fr.barlords.dndgateapi.domain.functional.service;

import fr.barlords.dndgateapi.domain.functional.exception.account.TokenNotValidException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CurrentAccountServiceTest {

    @InjectMocks
    private CurrentAccountService service;

    @Mock
    private AccountPersistenceSpi spi;

    @Test
    void should_not_get_account_because_it_does_not_exist_anymore() {
        UUID accountId = UUID.randomUUID();

        when(spi.findById(accountId)).thenReturn(Optional.empty());

        Assertions.assertThrows(TokenNotValidException.class, () -> service.getAccountById(accountId));
    }

    @Test
    void should_get_account() {
        Account account = Account.builder().pseudo("accounttest").email("test@test.fr").build();

        when(spi.findById(account.getId())).thenReturn(Optional.of(account));

        Assertions.assertEquals(account, service.getAccountById(account.getId()));
    }
}
