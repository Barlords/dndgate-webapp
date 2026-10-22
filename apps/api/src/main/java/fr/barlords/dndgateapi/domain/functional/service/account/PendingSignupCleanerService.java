package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.port.in.account.PendingSignupCleanerApi;
import fr.barlords.dndgateapi.domain.port.out.PendingSignupPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.time.Clock;

@RequiredArgsConstructor
public class PendingSignupCleanerService implements PendingSignupCleanerApi {

    private final PendingSignupPersistenceSpi pendingSignups;

    private final Clock clock;

    @Override
    public void purgeExpired() {
        pendingSignups.deleteExpiredBefore(clock.instant());
    }

}
