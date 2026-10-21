package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.exception.account.AccountNotValidatedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.EmailAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.exception.account.PseudoAlreadyUsedException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class AccountCheckerService {

    private final AccountPersistenceSpi spi;

    public void pseudoIsAvailable(Account account) {
        Optional<Account> accountWithSamePseudo = spi.findByPseudo(account.getPseudo());
        if (accountWithSamePseudo.isPresent()) {
            throw new PseudoAlreadyUsedException();
        }
    }

    public void emailIsAvailable(Account account) {
        Optional<Account> accountWithSameEmail = spi.findByEmail(account.getEmail());
        if (accountWithSameEmail.isPresent()) {
            throw new EmailAlreadyUsedException();
        }
    }

    public void accountIsActivated(Account account) {
        if(!account.isActivated()) {
            throw new AccountNotValidatedException();
        }
    }
}
