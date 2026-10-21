package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.bootstrap.config.RetryingTransaction;
import fr.barlords.dndgateapi.bootstrap.config.TransactionalMasterTransfer;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.functional.service.adventure.master.MasterClaimService;
import fr.barlords.dndgateapi.domain.functional.service.adventure.master.MasterHandOverService;
import fr.barlords.dndgateapi.domain.functional.service.adventure.master.MasterInvitationService;
import fr.barlords.dndgateapi.domain.functional.service.adventure.master.MasterStepDownService;
import fr.barlords.dndgateapi.domain.functional.service.adventure.master.MasterTransferNotifier;
import fr.barlords.dndgateapi.domain.port.in.account.AccountFinderApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.master.MasterClaimApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.master.MasterInvitationApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.master.MasterStepDownApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import fr.barlords.dndgateapi.domain.port.out.MasterClaimPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MasterInvitationPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.OpaqueTokenSpi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class MasterTransferServiceConfiguration {

    @Bean
    public MasterTransferNotifier masterTransferNotifier(AccountFinderApi accountFinderApi, EmailingSpi emailingSpi) {
        return new MasterTransferNotifier(accountFinderApi, emailingSpi);
    }

    @Bean
    public MasterHandOverService masterHandOverService(
            AdventurePersistenceSpi adventures,
            MasterInvitationPersistenceSpi invitations,
            AccountFinderApi accountFinderApi,
            MasterTransferNotifier notifier
    ) {
        return new MasterHandOverService(adventures, invitations, accountFinderApi, notifier);
    }

    @Bean
    public MasterStepDownApi masterStepDownApi(
            AdventurePersistenceSpi adventures,
            MasterInvitationPersistenceSpi invitations,
            AdventureOwnerVerifierService adventureOwnerVerifierService,
            MasterTransferNotifier notifier,
            Clock clock,
            RetryingTransaction transaction
    ) {
        return new TransactionalMasterTransfer.StepDown(
                new MasterStepDownService(adventures, invitations, adventureOwnerVerifierService, notifier, clock), transaction);
    }

    @Bean
    public MasterInvitationApi masterInvitationApi(
            AdventurePersistenceSpi adventures,
            MasterInvitationPersistenceSpi invitations,
            AdventureOwnerVerifierService adventureOwnerVerifierService,
            CurrentAccountService currentAccountService,
            MasterHandOverService handOverService,
            OpaqueTokenSpi opaqueTokens,
            Clock clock,
            RetryingTransaction transaction,
            @Value("${game-master.invitation.ttl:P7D}") Duration timeToLive
    ) {
        return new TransactionalMasterTransfer.Invitation(new MasterInvitationService(
                adventures, invitations, adventureOwnerVerifierService, currentAccountService, handOverService,
                opaqueTokens, clock, timeToLive), transaction);
    }

    @Bean
    public MasterClaimApi masterClaimApi(
            AdventurePersistenceSpi adventures,
            MasterClaimPersistenceSpi claims,
            AdventureAccessVerifierApi adventureAccessVerifierApi,
            AccountFinderApi accountFinderApi,
            MasterHandOverService handOverService,
            MasterTransferNotifier notifier,
            Clock clock,
            RetryingTransaction transaction,
            @Value("${game-master.claim.ttl:P7D}") Duration timeToLive
    ) {
        return new TransactionalMasterTransfer.Claim(new MasterClaimService(
                adventures, claims, adventureAccessVerifierApi, accountFinderApi, handOverService, notifier, clock, timeToLive), transaction);
    }
}
