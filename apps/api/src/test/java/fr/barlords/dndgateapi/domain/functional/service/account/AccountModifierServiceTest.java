package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.out.PasswordHasherSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountModifierServiceTest {

    @InjectMocks
    private AccountModifierService service;

    @Mock
    private PasswordHasherSpi passwordHasher;

    @Test
    void should_encode_account_password_with_the_hasher() {
        Account given = Account.builder()
                .pseudo("accounttest")
                .email("test@test.fr")
                .password("accountpassword")
                .build();

        when(passwordHasher.hash("accountpassword")).thenReturn("hashed");

        Account actual = service.encodePassword(given);

        Assertions.assertEquals("hashed", actual.getPassword());
        Assertions.assertEquals("accountpassword", given.getPassword());
    }
}
