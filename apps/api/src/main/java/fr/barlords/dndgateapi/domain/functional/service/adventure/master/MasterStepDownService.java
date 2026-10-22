package fr.barlords.dndgateapi.domain.functional.service.adventure.master;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.master.AdventureNotTransferableException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.service.DeletionConfirmation;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.port.in.adventure.master.MasterStepDownApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MasterInvitationPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.UUID;

/** Not transactional by itself: the caller wraps it in a transaction. */
@Slf4j
@RequiredArgsConstructor
public class MasterStepDownService implements MasterStepDownApi {

    private final AdventurePersistenceSpi adventures;

    private final MasterInvitationPersistenceSpi invitations;

    private final AdventureOwnerVerifierService adventureOwnerVerifierService;

    private final MasterTransferNotifier notifier;

    private final Clock clock;

    @Override
    public Adventure stepDown(UUID accountId, UUID adventureId, String confirmation) {
        Adventure adventure = adventureOwnerVerifierService.verify(accountId, adventureId);

        DeletionConfirmation.requireName(adventure.getName(), confirmation);

        if (!adventure.isTransferable()) {
            throw new AdventureNotTransferableException();
        }

        Adventure vacant = adventures.save(adventure.withMasterVacantSince(clock.instant()));
        invitations.deleteByAdventureId(adventureId);
        notifier.masterSteppedDown(vacant);

        log.info("Account {} stepped down as game master of the adventure {}", accountId, adventureId);

        return vacant;
    }
}
