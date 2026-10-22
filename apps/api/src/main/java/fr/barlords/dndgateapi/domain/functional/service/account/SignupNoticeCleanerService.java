package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.port.in.account.SignupNoticeCleanerApi;
import fr.barlords.dndgateapi.domain.port.out.SignupNoticeLogSpi;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;

@RequiredArgsConstructor
public class SignupNoticeCleanerService implements SignupNoticeCleanerApi {

    private final SignupNoticeLogSpi noticeLog;

    private final Clock clock;

    /** How long a notice blocks the next one for the same address. */
    private final Duration window;

    @Override
    public void purgeExpired() {
        noticeLog.deleteSentBefore(clock.instant().minus(window));
    }

}
