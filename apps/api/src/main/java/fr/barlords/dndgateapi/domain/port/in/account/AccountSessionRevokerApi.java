package fr.barlords.dndgateapi.domain.port.in.account;

import java.util.UUID;

public interface AccountSessionRevokerApi {

    /** Ends one session: its access and refresh tokens stop working immediately. */
    void logout(UUID sessionId);

    /** Ends every session of the account, on every device. */
    void logoutAll(UUID accountId);

}
