package fr.barlords.dndgateapi.domain.port.in.account;

import fr.barlords.dndgateapi.domain.functional.model.account.AccountSession;

public interface AccountSessionRefresherApi {

    /**
     * Exchanges a refresh token for a new access token and a new refresh token (rotation).
     * A refresh token that was already used revokes the whole session.
     *
     * @throws fr.barlords.dndgateapi.domain.functional.exception.account.InvalidRefreshTokenException when it cannot be used
     */
    AccountSession refresh(String refreshToken);

}
