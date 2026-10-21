package fr.barlords.dndgateapi.domain.functional.service.adventure.master;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.AdventureNotTransferableException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.AlreadyMasterException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.MasterInvitationNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.IssuedMasterInvitation;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterInvitation;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.port.in.adventure.master.MasterInvitationApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MasterInvitationPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.OpaqueTokenSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Not transactional by itself: the caller wraps it in a transaction. */
@Slf4j
@RequiredArgsConstructor
public class MasterInvitationService implements MasterInvitationApi {

    private final AdventurePersistenceSpi adventures;

    private final MasterInvitationPersistenceSpi invitations;

    private final AdventureOwnerVerifierService adventureOwnerVerifierService;

    private final CurrentAccountService currentAccountService;

    private final MasterHandOverService handOverService;

    private final OpaqueTokenSpi opaqueTokens;

    private final Clock clock;

    private final Duration timeToLive;

    @Override
    public IssuedMasterInvitation invite(UUID accountId, UUID adventureId) {
        Adventure adventure = adventureOwnerVerifierService.verify(accountId, adventureId);

        if (!adventure.isTransferable()) {
            throw new AdventureNotTransferableException();
        }

        String code = opaqueTokens.generate();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(timeToLive);

        invitations.replace(MasterInvitation.builder()
                .adventureId(adventureId)
                .codeHash(opaqueTokens.hash(code))
                .createdBy(accountId)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build());

        return new IssuedMasterInvitation(code, expiresAt);
    }

    @Override
    public void revoke(UUID accountId, UUID adventureId) {
        adventureOwnerVerifierService.verify(accountId, adventureId);

        invitations.deleteByAdventureId(adventureId);
    }

    @Override
    public Adventure accept(UUID accountId, String code) {
        Account account = currentAccountService.getAccountById(accountId);
        if (account.isAnonymized()) {
            throw new MasterInvitationNotFoundException();
        }

        // An unknown code, an expired one and one of an adventure that cannot change master all look the same
        MasterInvitation invitation = invitations.findByCodeHash(opaqueTokens.hash(code))
                .filter(found -> found.isValidAt(clock.instant()))
                .orElseThrow(MasterInvitationNotFoundException::new);

        Adventure adventure = adventures.findById(invitation.getAdventureId())
                .filter(found -> !found.isMasterVacant() && found.isTransferable())
                .orElseThrow(MasterInvitationNotFoundException::new);

        if (adventure.isMaster(accountId)) {
            throw new AlreadyMasterException();
        }

        // A code works once, even if two people use it at the same moment
        if (!invitations.consume(invitation.getId())) {
            throw new MasterInvitationNotFoundException();
        }

        log.info("Account {} became game master of the adventure {} by invitation", accountId, adventure.getId());

        return handOverService.handOver(adventure, accountId);
    }
}
