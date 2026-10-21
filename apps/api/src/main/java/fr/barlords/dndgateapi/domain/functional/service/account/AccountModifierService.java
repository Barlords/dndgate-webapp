package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.out.PasswordHasherSpi;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AccountModifierService {

    private final PasswordHasherSpi passwordHasher;

    public Account encodePassword(Account account) {
        return account.withPassword(passwordHasher.hash(account.getPassword()));
    }

}
