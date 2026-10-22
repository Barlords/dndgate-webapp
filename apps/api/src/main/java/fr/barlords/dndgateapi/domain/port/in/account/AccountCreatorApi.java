package fr.barlords.dndgateapi.domain.port.in.account;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;

public interface AccountCreatorApi {

    /**
     * Records a pending signup and sends its confirmation mail (no account exists until the link is followed), or
     * tells the owner of the address when it is already registered. A recent pending signup for the address is
     * left as it is, in silence. Returns nothing: the caller must not be able to tell these cases apart.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.account.PseudoAlreadyUsedException pseudos are public
     */
    void create(Account account);

}
