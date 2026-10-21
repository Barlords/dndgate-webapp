package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.port.in.account.SessionVerifierApi;
import fr.barlords.dndgateapi.domain.port.out.SessionPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.UUID;

@RequiredArgsConstructor
public class SessionVerifierService implements SessionVerifierApi {

    private final SessionPersistenceSpi sessions;

    private final Clock clock;

    @Override
    public boolean isActive(UUID sessionId) {
        return sessions.isSessionActive(sessionId, clock.instant());
    }

}
