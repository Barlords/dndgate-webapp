package fr.barlords.dndgateapi.domain.port.in.account;

import fr.barlords.dndgateapi.domain.functional.model.account.AccountSession;

public interface AccountLoggerApi {

    AccountSession login(String pseudo, String password);

}
