package fr.barlords.dndgateapi.domain.port.in.account;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;

public interface AccountCreatorApi {

    /**
     * Creates the account, or tells the owner of the address when it is already registered. Returns nothing: the
     * caller must not be able to tell the two cases apart.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.account.PseudoAlreadyUsedException pseudos are public
     */
    void create(Account account);

}
