package fr.barlords.dndgateapi.domain.functional.service.spellbook;

import fr.barlords.dndgateapi.domain.functional.exception.account.TokenNotValidException;
import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import fr.barlords.dndgateapi.domain.functional.model.SpellBook;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpellBookCreatorServiceTest {

    @Mock
    private SpellBookPersistenceSpi spi;

    @Mock
    private CurrentAccountService currentAccountService;

    private SpellBookCreatorService service;

    private final Account wizard = Account.builder().pseudo("wizard").email("wizard@test.fr").build();

    @BeforeEach
    void setUp() {
        service = new SpellBookCreatorService(spi, currentAccountService, QuantityLimits.defaults());
    }

    @Test
    void the_spellbook_belongs_to_the_caller_whatever_the_request_says() {
        when(currentAccountService.getAccountById(wizard.getId())).thenReturn(wizard);
        when(spi.save(any(SpellBook.class))).thenAnswer(i -> i.getArguments()[0]);
        Account someoneElse = Account.builder().pseudo("other").build();
        SpellBook draft = SpellBook.builder().owner(someoneElse).name("Grimoire").description("Fire").spellList(Set.of("Fireball")).build();

        SpellBook created = service.create(wizard.getId(), draft);

        Assertions.assertEquals(wizard, created.getOwner());
        Assertions.assertEquals("Grimoire", created.getName());
        Assertions.assertEquals(Set.of("Fireball"), created.getSpellList());
    }

    @Test
    void an_unknown_caller_creates_nothing() {
        when(currentAccountService.getAccountById(wizard.getId())).thenThrow(new TokenNotValidException());

        Assertions.assertThrows(TokenNotValidException.class,
                () -> service.create(wizard.getId(), SpellBook.builder().name("X").build()));

        verify(spi, never()).save(any());
    }

    @Test
    void a_failed_save_is_reported() {
        when(currentAccountService.getAccountById(wizard.getId())).thenReturn(wizard);
        when(spi.save(any(SpellBook.class))).thenThrow(new IllegalStateException("db down"));

        Assertions.assertThrows(IllegalStateException.class,
                () -> service.create(wizard.getId(), SpellBook.builder().name("X").build()));
    }
}
