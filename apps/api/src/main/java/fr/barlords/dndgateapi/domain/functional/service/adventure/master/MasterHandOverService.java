package fr.barlords.dndgateapi.domain.functional.service.adventure.master;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.port.in.account.AccountFinderApi;
import fr.barlords.dndgateapi.domain.port.out.AccessRevocationSpi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MasterInvitationPersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The one place where the game master of an adventure changes.
 * <p>
 * The new master is no longer a player. The former master becomes a player when there is a seat for them and
 * their account still exists; otherwise they leave the adventure (their characters stay).
 * <p>
 * Not transactional by itself: the caller wraps it in a transaction.
 */
@RequiredArgsConstructor
public class MasterHandOverService {

    private final AdventurePersistenceSpi adventures;

    private final MasterInvitationPersistenceSpi invitations;

    private final AccountFinderApi accountFinderApi;

    private final MasterTransferNotifier notifier;

    private final AccessRevocationSpi accessRevocation;

    public Adventure handOver(Adventure adventure, UUID newMasterId) {
        UUID formerMasterId = adventure.getOwnerId();

        List<UUID> players = new ArrayList<>(adventure.getPlayers());
        players.remove(newMasterId);

        Adventure handedOver = adventure
                .withOwnerId(newMasterId)
                .withMasterVacantSince(null)
                .withPlayers(players);

        if (!formerMasterId.equals(newMasterId) && stillExists(formerMasterId) && !handedOver.isFull()) {
            players.add(formerMasterId);
            handedOver = handedOver.withPlayers(players);
        }

        invitations.deleteByAdventureId(adventure.getId());

        Adventure saved = adventures.save(handedOver).getOrElseThrow(DataNotSaveException::new);
        notifier.masterChanged(saved, formerMasterId);

        if (!saved.isParticipant(formerMasterId)) {
            // no seat left for the former master: their chat and WebRTC signals stop with their participation
            accessRevocation.participationEnded(formerMasterId, saved.getId());
        }

        return saved;
    }

    private boolean stillExists(UUID accountId) {
        return accountFinderApi.findById(accountId).filter(account -> !account.isAnonymized()).isPresent();
    }
}
