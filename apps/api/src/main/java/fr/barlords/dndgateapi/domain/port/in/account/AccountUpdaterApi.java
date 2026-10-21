package fr.barlords.dndgateapi.domain.port.in.account;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.AccountChanges;

import java.util.UUID;

public interface AccountUpdaterApi {

    /**
     * Changes the e-mail, the pseudo and/or the password. The current password is required: an access
     * token alone, which can be stolen, must not be enough to take over an account.
     * Changing the password ends every session.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.account.WrongPasswordException when the current password is wrong
     */
    Account update(UUID accountId, String currentPassword, AccountChanges changes);

}
