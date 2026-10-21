package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.port.in.account.SessionCleanerApi;
import fr.barlords.dndgateapi.domain.port.out.SessionPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.time.Clock;

@RequiredArgsConstructor
public class SessionCleanerService implements SessionCleanerApi {

    private final SessionPersistenceSpi sessions;

    private final Clock clock;

    @Override
    public void purgeExpired() {
        sessions.deleteExpiredSessions(clock.instant());
    }

}
