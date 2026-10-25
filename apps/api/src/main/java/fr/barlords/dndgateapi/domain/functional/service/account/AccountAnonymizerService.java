package fr.barlords.dndgateapi.domain.functional.service.account;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.port.out.LoginFailureSpi;
import fr.barlords.dndgateapi.domain.functional.exception.account.AccountNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.account.WrongPasswordException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.service.adventure.master.MasterTransferNotifier;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaim;
import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaimStatus;
import fr.barlords.dndgateapi.domain.port.in.account.AccountAnonymizerApi;
import fr.barlords.dndgateapi.domain.port.in.account.AccountSessionRevokerApi;
import fr.barlords.dndgateapi.domain.port.out.AccountPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MagicItemPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MasterClaimPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import fr.barlords.dndgateapi.domain.port.out.OpaqueTokenSpi;
import fr.barlords.dndgateapi.domain.port.out.PasswordHasherSpi;
import fr.barlords.dndgateapi.domain.port.out.SpellBookPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    private final MasterClaimPersistenceSpi masterClaims;

    private final SpellBookPersistenceSpi spellBooks;

    private final MagicItemPersistenceSpi magicItems;

    private final GameCharacterPersistenceSpi gameCharacters;

    private final MinioSpi minio;

    private final PasswordHasherSpi passwordHasher;

    private final OpaqueTokenSpi opaqueTokens;

    private final AccountPasswordVerifierService passwordVerifier;

    private final AccountSessionRevokerApi sessionRevoker;

    private final MasterTransferNotifier masterTransferNotifier;

    private final Clock clock;

    private final AuditSpi audit;

    private final LoginFailureSpi loginFailures;

    @Override
    public void anonymize(UUID accountId, String password, ClientAddress client) {
        verify(accountId, password, client).ifPresent(this::apply);
    }

    /**
     * First step of {@link #anonymize}: reads the account and checks the password (a wrong one is counted as a failed
     * login), so that it can run before the transaction of the anonymization opens.
     *
     * @return the account to anonymize, or nothing when it already is (its last sessions are ended)
     */
    public Optional<Account> verify(UUID accountId, String password, ClientAddress client) {
        Account account = accounts.findById(accountId)
                .orElseThrow(AccountNotFoundException::new);

        if (account.isAnonymized()) {
            sessionRevoker.logoutAll(accountId);
            return Optional.empty();
        }

        passwordVerifier.verify(account, password, client, WrongPasswordException::new);
        return Optional.of(account);
    }

    /**
     * Second step of {@link #anonymize}, to run in one transaction. The account keeps the version read by
     * {@link #verify}: if it changed since, its save is refused and nothing is kept.
     */
    public void apply(Account account) {
        UUID accountId = account.getId();

        abortOwnedAdventuresThatHaveNotStarted(accountId);
        freeSeatsInAdventuresThatHaveNotStarted(accountId);
        cancelOpenMasterClaims(account);
        deleteSpellBooks(accountId);
        deleteMagicItems(accountId);
        deleteCharactersWithoutAdventure(accountId);

        accounts.save(anonymousVersionOf(account));

        loginFailures.deleteByAccount(accountId);

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
                .withFounder(false)
                .withPendingEmail(null)
                .withPendingEmailCode(null)
                .withPendingEmailExpiresAt(null)
                .withAnonymizedAt(clock.instant());
    }

    /** Nobody is left to start them: they must not stay listed and joinable. */
    private void abortOwnedAdventuresThatHaveNotStarted(UUID accountId) {
        for (Adventure adventure : adventures.findAllByCreatorId(accountId)) {
            if (adventure.isOpenToNewPlayers()) {
                adventures.save(adventure
                                .withStatus(AdventureStatus.ABORT)
                                .withVisibility(AdventureVisibility.PRIVATE));
            } else if (adventure.isTransferable() && !adventure.isMasterVacant()) {
                // A running adventure goes on: the players can invite a new master or vote for one among them
                Adventure vacant = adventures.save(adventure.withMasterVacantSince(clock.instant()));
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
                adventures.save(adventure.withPlayers(players));
            }
        }
    }

    /** An account that no longer exists cannot become game master: its votes end, like a voluntary cancel. */
    private void cancelOpenMasterClaims(Account account) {
        for (MasterClaim claim : masterClaims.findOpenByCandidateId(account.getId())) {
            masterClaims.save(claim.withStatus(MasterClaimStatus.CANCELLED));

            adventures.findById(claim.getAdventureId()).ifPresent(adventure -> {
                List<UUID> voters = adventure.getPlayers().stream()
                        .filter(player -> !player.equals(account.getId()))
                        .toList();
                // The mails leave after the commit, with the pseudo as it is now
                masterTransferNotifier.claimCancelled(adventure, account.getId(), voters);
            });
        }
    }

    private void deleteSpellBooks(UUID accountId) {
        spellBooks.findByOwnerId(accountId)
                .forEach(spellBook -> spellBooks.deleteById(spellBook.getId()));
    }

    private void deleteMagicItems(UUID accountId) {
        magicItems.findByOwnerId(accountId)
                .forEach(magicItem -> magicItems.deleteById(magicItem.getId()));
    }

    /** Nobody else reads a character kept outside any adventure: it goes with its picture. Those of adventures stay. */
    private void deleteCharactersWithoutAdventure(UUID accountId) {
        for (GameCharacter character : gameCharacters.findByOwnerId(accountId)) {
            if (!character.hasAdventure()) {
                gameCharacters.deleteById(character.getId());
                if (character.getPicturePath() != null) {
                    minio.deleteAfterCommit(character.getPicturePath());
                }
            }
        }
    }

}
