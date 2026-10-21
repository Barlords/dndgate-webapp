package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.exception.account.AccountNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.account.WrongPasswordException;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.service.adventure.master.MasterTransferNotifier;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;
import fr.barlords.dndgateapi.domain.port.in.account.AccountAnonymizerApi;
import fr.barlords.dndgateapi.domain.port.in.account.AccountSessionRevokerApi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.OpaqueTokenSpi;
import fr.barlords.dndgateapi.domain.port.out.PasswordHasherSpi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Not transactional by itself: the caller wraps it in a transaction so that an account is never
 * left half anonymized.
 */
@Slf4j
@RequiredArgsConstructor
public class AccountAnonymizerService implements AccountAnonymizerApi {

    private final AccountPersistenceSpi accounts;

    private final AdventurePersistenceSpi adventures;

    private final SpellBookPersistenceSpi spellBooks;

    private final PasswordHasherSpi passwordHasher;

    private final OpaqueTokenSpi opaqueTokens;

    private final AccountPasswordVerifierService passwordVerifier;

    private final AccountSessionRevokerApi sessionRevoker;

    private final MasterTransferNotifier masterTransferNotifier;

    private final Clock clock;

    private final AuditSpi audit;

    @Override
    public void anonymize(UUID accountId, String password) {
        Account account = accounts.findById(accountId)
                .orElseThrow(AccountNotFoundException::new);

        if (account.isAnonymized()) {
            sessionRevoker.logoutAll(accountId);
            return;
        }

        passwordVerifier.verify(account, password, WrongPasswordException::new);

        abortOwnedAdventuresThatHaveNotStarted(accountId);
        freeSeatsInAdventuresThatHaveNotStarted(accountId);
        deleteSpellBooks(accountId);

        accounts.save(anonymousVersionOf(account))
                .getOrElseThrow(DataNotSaveException::new);

        sessionRevoker.logoutAll(accountId);

        audit.deletion("account", accountId, accountId);
        log.info("Account {} anonymized", accountId);
    }

    private Account anonymousVersionOf(Account account) {
        String hex = account.getId().toString().replace("-", "");

        return account
                .withPseudo("deleted-" + hex.substring(0, 12))
                .withEmail(hex + "@anonymized.invalid")
                // nobody knows this password, the hash can never match
                .withPassword(passwordHasher.hash(opaqueTokens.generate()))
                .withActivated(false)
                .withVerificationCode(null)
                .withPendingEmail(null)
                .withPendingEmailCode(null)
                .withPendingEmailExpiresAt(null)
                .withFailedLoginAttempts(0)
                .withLockedUntil(null)
                .withAnonymizedAt(clock.instant());
    }

    /** Nobody is left to start them: they must not stay listed and joinable. */
    private void abortOwnedAdventuresThatHaveNotStarted(UUID accountId) {
        for (Adventure adventure : adventures.findAllByCreatorId(accountId)) {
            if (adventure.isOpenToNewPlayers()) {
                adventures.save(adventure
                                .withStatus(AdventureStatus.ABORT)
                                .withVisibility(AdventureVisibility.PRIVATE))
                        .getOrElseThrow(DataNotSaveException::new);
            } else if (adventure.isTransferable() && !adventure.isMasterVacant()) {
                // A running adventure goes on: the players can invite a new master or vote for one among them
                Adventure vacant = adventures.save(adventure.withMasterVacantSince(clock.instant()))
                        .getOrElseThrow(DataNotSaveException::new);
                masterTransferNotifier.masterAccountDeleted(vacant);
            }
        }
    }

    /** Past and running adventures keep the account among their players: their history stays readable. */
    private void freeSeatsInAdventuresThatHaveNotStarted(UUID accountId) {
        for (Adventure adventure : adventures.findAllByPlayersId(accountId)) {
            if (adventure.isOpenToNewPlayers()) {
                List<UUID> players = new ArrayList<>(adventure.getPlayers());
                players.remove(accountId);
                adventures.save(adventure.withPlayers(players))
                        .getOrElseThrow(DataNotSaveException::new);
            }
        }
    }

    private void deleteSpellBooks(UUID accountId) {
        spellBooks.findByOwnerId(accountId)
                .forEach(spellBook -> spellBooks.deleteById(spellBook.getId()));
    }

}
