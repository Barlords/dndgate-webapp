package fr.barlords.dndgateapi.domain.port.in.account;

import fr.barlords.dndgateapi.domain.functional.model.account.AccountSession;
import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;

public interface AccountLoggerApi {

    AccountSession login(String pseudo, String password, ClientAddress client);

}
