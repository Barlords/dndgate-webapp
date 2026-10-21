package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.port.in.account.SessionCleanerApi;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SessionCleanupScheduler {

    private final SessionCleanerApi sessionCleanerApi;

    @Scheduled(cron = "${security.session.cleanup-cron:0 30 3 * * *}")
    public void purgeExpiredSessions() {
        sessionCleanerApi.purgeExpired();
    }

}
