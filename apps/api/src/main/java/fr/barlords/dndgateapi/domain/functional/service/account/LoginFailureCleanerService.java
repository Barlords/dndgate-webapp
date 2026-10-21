package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.port.in.account.LoginFailureCleanerApi;
import fr.barlords.dndgateapi.domain.port.out.LoginFailureSpi;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;

@RequiredArgsConstructor
public class LoginFailureCleanerService implements LoginFailureCleanerApi {

    private final LoginFailureSpi failures;

    private final Clock clock;

    /** A lock never outlives its last failure by more than this. */
    private final Duration lockDuration;

    @Override
    public void purgeExpired() {
        failures.deleteFailedBefore(clock.instant().minus(lockDuration));
    }

}
