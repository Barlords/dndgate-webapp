package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.port.in.account.AccountSessionRevokerApi;
import fr.barlords.dndgateapi.domain.port.out.SessionPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.UUID;

@RequiredArgsConstructor
public class AccountSessionRevokerService implements AccountSessionRevokerApi {

    private final SessionPersistenceSpi sessions;

    private final Clock clock;

    @Override
    public void logout(UUID sessionId) {
        sessions.revokeSession(sessionId, clock.instant());
    }

    @Override
    public void logoutAll(UUID accountId) {
        sessions.revokeAllSessionsOfAccount(accountId, clock.instant());
    }

}
