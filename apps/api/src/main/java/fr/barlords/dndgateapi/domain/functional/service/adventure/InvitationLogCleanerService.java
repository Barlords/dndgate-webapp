package fr.barlords.dndgateapi.domain.functional.service.adventure;

import fr.barlords.dndgateapi.domain.functional.model.adventure.InvitationLimits;
import fr.barlords.dndgateapi.domain.port.in.adventure.InvitationLogCleanerApi;
import fr.barlords.dndgateapi.domain.port.out.InvitationLogSpi;
import lombok.RequiredArgsConstructor;

import java.time.Clock;

@RequiredArgsConstructor
public class InvitationLogCleanerService implements InvitationLogCleanerApi {

    private final InvitationLogSpi invitationLog;

    private final Clock clock;

    private final InvitationLimits limits;

    @Override
    public void purgeExpired() {
        invitationLog.deleteSentBefore(clock.instant().minus(limits.longestWindow()));
    }

}
