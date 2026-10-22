package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountPersistenceSpi extends PersistenceSpi<Account, UUID> {

    Optional<Account> findByEmail(String email);

    Optional<Account> findByPseudo(String pseudo);

    /**
     * The accounts that asked to switch to this address and have not confirmed it yet. Nothing reserves an address
     * before it is confirmed, so several accounts can wait for the same one: the code tells which one it is.
     */
    List<Account> findAllByPendingEmail(String pendingEmail);

    Account update(Account o);

}
