package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.port.in.account.LoginFailureCleanerApi;
import fr.barlords.dndgateapi.domain.port.in.account.SessionCleanerApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.InvitationLogCleanerApi;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SessionCleanupScheduler {

    private final SessionCleanerApi sessionCleanerApi;

    private final InvitationLogCleanerApi invitationLogCleanerApi;

    private final LoginFailureCleanerApi loginFailureCleanerApi;

    @Scheduled(cron = "${security.session.cleanup-cron:0 30 3 * * *}")
    public void purgeExpiredSessions() {
        sessionCleanerApi.purgeExpired();
    }

    @Scheduled(cron = "${security.session.cleanup-cron:0 30 3 * * *}")
    public void purgeOldInvitations() {
        invitationLogCleanerApi.purgeExpired();
    }

    @Scheduled(cron = "${security.session.cleanup-cron:0 30 3 * * *}")
    public void purgeOldLoginFailures() {
        loginFailureCleanerApi.purgeExpired();
    }

}
