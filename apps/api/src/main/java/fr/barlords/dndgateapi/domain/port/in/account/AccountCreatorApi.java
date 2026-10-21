package fr.barlords.dndgateapi.domain.port.in.account;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;

public interface AccountCreatorApi {

    Account create(Account account);

}
