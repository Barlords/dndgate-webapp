package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.port.in.account.AccountFinderApi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AccountFinderService implements AccountFinderApi {

    private final AccountPersistenceSpi spi;

    @Override
    public Optional<Account> findById(UUID id) {
        return spi.findById(id);
    }

    @Override
    public Optional<Account> findByEmail(String email) {
        return spi.findByEmail(email);
    }

    @Override
    public Optional<Account> findByPseudo(String pseudo) {
        return spi.findByPseudo(pseudo);
    }

}
