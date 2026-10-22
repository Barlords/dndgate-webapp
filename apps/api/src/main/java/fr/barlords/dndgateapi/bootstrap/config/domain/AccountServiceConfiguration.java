package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.port.out.MasterClaimPersistenceSpi;
import fr.barlords.dndgateapi.domain.functional.service.adventure.master.MasterTransferNotifier;

import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.account.*;
import fr.barlords.dndgateapi.domain.port.in.account.*;
import fr.barlords.dndgateapi.domain.port.out.AccessRevocationSpi;
import fr.barlords.dndgateapi.domain.port.out.AccessTokenSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import fr.barlords.dndgateapi.domain.port.out.LoginFailureSpi;
import fr.barlords.dndgateapi.domain.port.out.PasswordHasherSpi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.bootstrap.config.TransactionalAccountAnonymizer;
import fr.barlords.dndgateapi.bootstrap.config.TransactionalAccountUpdater;
import fr.barlords.dndgateapi.bootstrap.config.TransactionalAccountVerifier;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.OpaqueTokenSpi;
import fr.barlords.dndgateapi.domain.port.out.PendingSignupPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.SignupNoticeLogSpi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.SessionPersistenceSpi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class AccountServiceConfiguration {

    @Bean
    public CurrentAccountService currentAccountService(
            AccountPersistenceSpi spi
    ) {
        return new CurrentAccountService(spi);
    }

    @Bean
    public AccountCheckerService accountCheckerService(
            AccountPersistenceSpi spi
    ) {
        return new AccountCheckerService(spi);
    }

    @Bean
    public AccountModifierService accountModifierService(
            PasswordHasherSpi passwordHasher
    ) {
        return new AccountModifierService(passwordHasher);
    }

    @Bean
    public AccountCreatorApi accountCreatorApi(
            AccountPersistenceSpi spi,
            AccountCheckerService accountCheckerService,
            AccountModifierService accountModifierService,
            PendingSignupPersistenceSpi pendingSignups,
            EmailingSpi emailingSpi,
            Clock clock,
            @Value("${security.verification.ttl:PT24H}") Duration verificationCodeTtl,
            @Value("${security.verification.resend-cooldown:PT1M}") Duration resendCooldown,
            SignupNoticeLogSpi noticeLog,
            @Value("${security.signup-notice.window:PT24H}") Duration noticeWindow
    ) {
        return new AccountCreatorService(spi, accountCheckerService, accountModifierService, pendingSignups, emailingSpi,
                clock, verificationCodeTtl, resendCooldown, noticeLog, noticeWindow);
    }

    @Bean
    public PendingSignupCleanerApi pendingSignupCleanerApi(PendingSignupPersistenceSpi pendingSignups, Clock clock) {
        return new PendingSignupCleanerService(pendingSignups, clock);
    }

    @Bean
    public SignupNoticeCleanerApi signupNoticeCleanerApi(
            SignupNoticeLogSpi noticeLog,
            Clock clock,
            @Value("${security.signup-notice.window:PT24H}") Duration noticeWindow
    ) {
        return new SignupNoticeCleanerService(noticeLog, clock, noticeWindow);
    }

    @Bean
    public AccountAnonymizerApi accountAnonymizerApi(
            AccountPersistenceSpi accounts,
            AdventurePersistenceSpi adventures,
            MasterClaimPersistenceSpi masterClaims,
            SpellBookPersistenceSpi spellBooks,
            PasswordHasherSpi passwordHasher,
            OpaqueTokenSpi opaqueTokenSpi,
            AccountPasswordVerifierService accountPasswordVerifierService,
            AccountSessionRevokerApi accountSessionRevokerApi,
            MasterTransferNotifier masterTransferNotifier,
            Clock clock,
            AuditSpi audit,
            LoginFailureSpi loginFailures,
            PlatformTransactionManager transactionManager
    ) {
        return new TransactionalAccountAnonymizer(new AccountAnonymizerService(
                accounts, adventures, masterClaims, spellBooks, passwordHasher, opaqueTokenSpi,
                accountPasswordVerifierService,
                accountSessionRevokerApi, masterTransferNotifier, clock, audit, loginFailures), transactionManager);
    }

    @Bean
    public AccountFinderApi accountFinderApi(
            AccountPersistenceSpi spi
    ) {
        return new AccountFinderService(spi);
    }

    @Bean
    public AccountUpdaterApi accountUpdaterApi(
            AccountPersistenceSpi spi,
            CurrentAccountService currentAccountService,
            AccountCheckerService accountCheckerService,
            AccountModifierService accountModifierService,
            AccountPasswordVerifierService accountPasswordVerifierService,
            AccountSessionRevokerApi accountSessionRevokerApi,
            EmailingSpi emailingSpi,
            Clock clock,
            @Value("${security.verification.ttl:PT24H}") Duration emailChangeCodeTtl,
            PlatformTransactionManager transactionManager
    ) {
        return new TransactionalAccountUpdater(new AccountUpdaterService(spi, currentAccountService, accountCheckerService,
                accountModifierService, accountPasswordVerifierService, accountSessionRevokerApi, emailingSpi, clock,
                emailChangeCodeTtl), transactionManager);
    }

    @Bean
    public AccountEmailChangeConfirmerApi accountEmailChangeConfirmerApi(
            AccountPersistenceSpi spi,
            AccountCheckerService accountCheckerService,
            Clock clock
    ) {
        return new AccountEmailChangeConfirmerService(spi, accountCheckerService, clock);
    }

    @Bean
    public AccountLoggerApi accountLoggerApi(
            AccountPersistenceSpi spi,
            PasswordHasherSpi passwordHasher,
            AccountPasswordVerifierService accountPasswordVerifierService,
            SessionIssuerService sessionIssuerService,
            AccountCheckerService accountCheckerService
    ) {
        return new AccountLoggerService(spi, passwordHasher, accountPasswordVerifierService, sessionIssuerService,
                accountCheckerService);
    }

    @Bean
    public AccountPasswordVerifierService accountPasswordVerifierService(
            LoginFailureSpi loginFailures,
            PasswordHasherSpi passwordHasher,
            Clock clock,
            @Value("${security.login.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${security.login.lock-duration:PT15M}") Duration lockDuration,
            @Value("${security.login.distinct-clients-warning-threshold:50}") int distinctClientsWarningThreshold
    ) {
        return new AccountPasswordVerifierService(loginFailures, passwordHasher, clock, maxFailedAttempts, lockDuration,
                distinctClientsWarningThreshold);
    }

    @Bean
    public SessionIssuerService sessionIssuerService(
            SessionPersistenceSpi sessions,
            AccessTokenSpi accessTokenSpi,
            OpaqueTokenSpi opaqueTokenSpi,
            Clock clock,
            @Value("${security.refresh.ttl:P60D}") Duration refreshTokenTtl,
            @Value("${security.session.max-lifetime:P180D}") Duration sessionMaxLifetime
    ) {
        return new SessionIssuerService(sessions, accessTokenSpi, opaqueTokenSpi, clock, refreshTokenTtl, sessionMaxLifetime);
    }

    @Bean
    public AccountSessionRefresherApi accountSessionRefresherApi(
            AccountPersistenceSpi accounts,
            SessionPersistenceSpi sessions,
            OpaqueTokenSpi opaqueTokenSpi,
            SessionIssuerService sessionIssuerService,
            Clock clock,
            AccessRevocationSpi accessRevocation
    ) {
        return new AccountSessionRefresherService(accounts, sessions, opaqueTokenSpi, sessionIssuerService, clock,
                accessRevocation);
    }

    @Bean
    public AccountSessionRevokerApi accountSessionRevokerApi(
            SessionPersistenceSpi sessions,
            Clock clock,
            AccessRevocationSpi accessRevocation
    ) {
        return new AccountSessionRevokerService(sessions, clock, accessRevocation);
    }

    @Bean
    public SessionVerifierApi sessionVerifierApi(SessionPersistenceSpi sessions, Clock clock) {
        return new SessionVerifierService(sessions, clock);
    }

    @Bean
    public SessionCleanerApi sessionCleanerApi(SessionPersistenceSpi sessions, Clock clock) {
        return new SessionCleanerService(sessions, clock);
    }

    @Bean
    public LoginFailureCleanerApi loginFailureCleanerApi(
            LoginFailureSpi loginFailures,
            Clock clock,
            @Value("${security.login.lock-duration:PT15M}") Duration lockDuration
    ) {
        return new LoginFailureCleanerService(loginFailures, clock, lockDuration);
    }

    @Bean
    public AccountVerifierApi accountVerifierApi(
            AccountPersistenceSpi accounts,
            PendingSignupPersistenceSpi pendingSignups,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        return new TransactionalAccountVerifier(new AccountVerifierService(accounts, pendingSignups, clock), transactionManager);
    }

    @Bean
    public AccountVerificationResenderApi accountVerificationResenderApi(
            PendingSignupPersistenceSpi pendingSignups,
            EmailingSpi emailingSpi,
            Clock clock,
            @Value("${security.verification.ttl:PT24H}") Duration verificationCodeTtl,
            @Value("${security.verification.resend-cooldown:PT1M}") Duration resendCooldown
    ) {
        return new AccountVerificationResenderService(pendingSignups, emailingSpi, clock, verificationCodeTtl, resendCooldown);
    }
}
