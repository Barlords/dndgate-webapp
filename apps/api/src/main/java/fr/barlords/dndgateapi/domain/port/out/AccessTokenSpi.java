package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.functional.model.account.AccessToken;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;

import java.util.UUID;

public interface AccessTokenSpi {

    AccessToken issueFor(Account account, UUID sessionId);

}
