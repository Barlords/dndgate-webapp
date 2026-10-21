package fr.barlords.dndgateapi.domain.port.in.account;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;

import java.util.Optional;
import java.util.UUID;

public interface AccountFinderApi {

    Optional<Account> findById(UUID id);

    Optional<Account> findByEmail(String email);

    Optional<Account> findByPseudo(String pseudo);

}
