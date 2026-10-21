package fr.barlords.dndgateapi.domain.functional.service.adventure.master;

import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterTransferEvent;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterTransferMessage;
import fr.barlords.dndgateapi.domain.port.in.account.AccountFinderApi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Tells the people concerned that the game master of their adventure changes. Who is told what is decided here;
 * how and when the e-mail leaves is up to the mailing adapter. Accounts that were deleted are never written to.
 */
@RequiredArgsConstructor
public class MasterTransferNotifier {

    private final AccountFinderApi accountFinderApi;

    private final EmailingSpi emailingSpi;

    /** The game master gave up the role: the players are told by whom. */
    public void masterSteppedDown(Adventure adventure) {
        String pseudo = account(adventure.getOwnerId()).map(Account::getPseudo).orElse(null);

        tell(adventure.getPlayers(), adventure, MasterTransferEvent.MASTER_STEPPED_DOWN, pseudo);
    }

    /** The account of the game master was deleted: nothing is said about who it was. */
    public void masterAccountDeleted(Adventure adventure) {
        tell(adventure.getPlayers(), adventure, MasterTransferEvent.MASTER_ACCOUNT_DELETED, null);
    }

    public void claimOpened(Adventure adventure, UUID candidateId, List<UUID> voters) {
        tell(voters, adventure, MasterTransferEvent.CLAIM_OPENED, pseudoOf(candidateId));
    }

    public void claimCancelled(Adventure adventure, UUID candidateId, List<UUID> voters) {
        tell(voters, adventure, MasterTransferEvent.CLAIM_CANCELLED, pseudoOf(candidateId));
    }

    public void claimRejected(Adventure adventure, UUID candidateId) {
        tell(List.of(candidateId), adventure, MasterTransferEvent.CLAIM_REJECTED, null);
    }

    /** The adventure as it is after the change: its owner is the new master, its players do not include them. */
    public void masterChanged(Adventure adventure, UUID formerMasterId) {
        UUID newMasterId = adventure.getOwnerId();
        String newMaster = pseudoOf(newMasterId);

        tell(List.of(newMasterId), adventure, MasterTransferEvent.YOU_ARE_NOW_MASTER, null);

        if (!formerMasterId.equals(newMasterId)) {
            tell(List.of(formerMasterId), adventure, MasterTransferEvent.YOU_ARE_NO_LONGER_MASTER, newMaster);
        }

        List<UUID> others = adventure.getPlayers().stream()
                .filter(player -> !player.equals(formerMasterId) && !player.equals(newMasterId))
                .toList();
        tell(others, adventure, MasterTransferEvent.MASTER_CHANGED, newMaster);
    }

    private void tell(List<UUID> recipients, Adventure adventure, MasterTransferEvent event, String otherPseudo) {
        MasterTransferMessage message = new MasterTransferMessage(event, adventure.getId(), adventure.getName(), otherPseudo);

        recipients.stream()
                .distinct()
                .map(this::account)
                .flatMap(Optional::stream)
                .forEach(receiver -> emailingSpi.sendMasterTransferEmail(receiver, message));
    }

    private Optional<Account> account(UUID accountId) {
        return accountFinderApi.findById(accountId).filter(account -> !account.isAnonymized());
    }

    private String pseudoOf(UUID accountId) {
        return account(accountId).map(Account::getPseudo).filter(Objects::nonNull).orElse(null);
    }
}
