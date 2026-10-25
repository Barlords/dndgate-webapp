package fr.barlords.dndgateapi.domain.functional.service.adventure;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureConcurrentModificationException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotJoinableException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureFullException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.InvalidStatusTransitionException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.InvitationRateLimitException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.MaxPlayerBelowPlayersException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.PlayerNotInAdventureException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.PlayersAlreadyInAdventureException;
import fr.barlords.dndgateapi.domain.functional.exception.account.AccountNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.DeletionNotConfirmedException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.plan.PlanLimit;
import fr.barlords.dndgateapi.domain.functional.service.DeletionConfirmation;
import fr.barlords.dndgateapi.domain.port.in.plan.EntitlementApi;
import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;
import fr.barlords.dndgateapi.domain.functional.model.adventure.InvitationLimits;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureFinderApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureUpdaterApi;
import fr.barlords.dndgateapi.domain.port.in.account.AccountFinderApi;
import fr.barlords.dndgateapi.domain.port.out.AccessRevocationSpi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import fr.barlords.dndgateapi.domain.port.out.InvitationLogSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class AdventureUpdaterService implements AdventureUpdaterApi {

    private static final int MAX_ATTEMPTS = 10;

    private final AdventurePersistenceSpi spi;

    private final AdventureFinderApi adventureFinderApi;

    private final CurrentAccountService currentAccountService;

    private final AdventureOwnerVerifierService adventureOwnerVerifierService;

    private final AccountFinderApi accountFinderApi;

    private final EmailingSpi emailingSpi;

    private final InvitationLogSpi invitationLog;

    private final Clock clock;

    private final InvitationLimits invitationLimits;

    private final AccessRevocationSpi accessRevocation;

    private final GameCharacterPersistenceSpi gameCharacters;

    private final MinioSpi minioSpi;

    private final AuditSpi audit;

    private final EntitlementApi entitlementApi;

    @Override
    public Adventure update(UUID accountId, Adventure adventure) {
        Adventure previousAdventure = adventureOwnerVerifierService.verify(accountId, adventure.getId());

        Adventure patchedAdventure = previousAdventure
                .withName(Objects.requireNonNullElse(adventure.getName(), previousAdventure.getName()))
                .withDescription(Objects.requireNonNullElse(adventure.getDescription(), previousAdventure.getDescription()))
                .withVisibility(Objects.requireNonNullElse(adventure.getVisibility(), previousAdventure.getVisibility()))
                .withPostalCode(Objects.requireNonNullElse(adventure.getPostalCode(), previousAdventure.getPostalCode()))
                .withCity(Objects.requireNonNullElse(adventure.getCity(), previousAdventure.getCity()))
                .withMaxPlayer(Objects.requireNonNullElse(adventure.getMaxPlayer(), previousAdventure.getMaxPlayer()));

        // The capacity can never drop under the players who already took a seat
        if (patchedAdventure.getMaxPlayer() < patchedAdventure.getPlayers().size()) {
            throw new MaxPlayerBelowPlayersException(patchedAdventure.getMaxPlayer(), patchedAdventure.getPlayers().size());
        }

        return spi.save(patchedAdventure);
    }

    @Override
    public Adventure updateVisibility(UUID accountId, UUID id, AdventureVisibility visibility) {
        Adventure adventure = adventureOwnerVerifierService.verify(accountId, id);

        return spi.save(adventure.withVisibility(visibility));
    }

    @Override
    public Adventure updateStatus(UUID accountId, UUID id, AdventureStatus status) {
        Adventure adventure = adventureOwnerVerifierService.verify(accountId, id);

        // Asking for the current status again changes nothing: the call is idempotent
        if (adventure.getStatus() == status) {
            return adventure;
        }

        if (!adventure.getStatus().canMoveTo(status)) {
            throw new InvalidStatusTransitionException(adventure.getStatus(), status);
        }

        return spi.save(adventure.withStatus(status));
    }

    @Override
    public Adventure joinAdventure(UUID adventureId, UUID accountId) {
        Account account = currentAccountService.getAccountById(accountId);

        // A private adventure is invisible to strangers: it can only be joined with its invitation code
        return retryOnConcurrentChange(() -> {
            Adventure adventure = adventureFinderApi.findByIdVisibleTo(accountId, adventureId)
                    .orElseThrow(AdventureNotFoundException::new);
            return join(account, adventure);
        });
    }

    @Override
    public Adventure joinAdventureByCode(UUID accountId, String code) {
        Account account = currentAccountService.getAccountById(accountId);

        return retryOnConcurrentChange(() -> {
            Adventure adventure = adventureFinderApi.findByInvitationCode(code)
                    .orElseThrow(AdventureNotFoundException::new);
            return join(account, adventure);
        });
    }

    private Adventure join(Account account, Adventure adventure) {
        if (adventure.isParticipant(account.getId())) {
            throw new PlayersAlreadyInAdventureException();
        }

        if (!adventure.isOpenToNewPlayers()) {
            throw new AdventureNotJoinableException();
        }

        if (adventure.isFull()) {
            throw new AdventureFullException();
        }

        List<UUID> players = new ArrayList<>(adventure.getPlayers());
        players.add(account.getId());
        Adventure joined = adventure.withPlayers(players);

        if (joined.isFull()) {
            joined = joined.withStatus(AdventureStatus.ONGOING).withVisibility(AdventureVisibility.PRIVATE);
        }

        return spi.save(joined);
    }

    @Override
    public void sendInvitationCode(UUID accountId, UUID adventureId, String targetPseudo) {
        Account senderAccount = currentAccountService.getAccountById(accountId);

        Adventure adventure = adventureOwnerVerifierService.verify(accountId, adventureId);

        Account targetedAccount = accountFinderApi.findByPseudo(targetPseudo.trim())
                .filter(found -> !found.isAnonymized())
                .orElseThrow(AccountNotFoundException::new);

        // An invitation only makes sense while the adventure accepts players, and for someone who is not in it yet
        if (!adventure.isOpenToNewPlayers()) {
            throw new AdventureNotJoinableException();
        }

        if (adventure.isParticipant(targetedAccount.getId())) {
            throw new PlayersAlreadyInAdventureException();
        }

        Instant now = clock.instant();
        checkInvitationLimits(accountId, targetedAccount.getId(), adventureId, now);

        invitationLog.record(accountId, targetedAccount.getId(), adventureId, now);
        emailingSpi.sendInvitationCodeEmail(senderAccount, targetedAccount, adventure.getInvitationCode());
    }

    /** An invitation is an e-mail sent in the name of the application: neither one sender nor one recipient may be flooded. */
    private void checkInvitationLimits(UUID senderId, UUID recipientId, UUID adventureId, Instant now) {
        invitationLog.findLastSentToRecipientSince(recipientId, adventureId, now.minus(invitationLimits.recipientWindow()))
                .ifPresent(last -> {
                    throw InvitationRateLimitException.recipientLimit(last.plus(invitationLimits.recipientWindow()));
                });

        List<Instant> sent = invitationLog.findSentAtBySenderSince(senderId, now.minus(invitationLimits.senderWindow()));
        if (sent.size() >= invitationLimits.senderMax()) {
            // The limit is free again when enough of the oldest invitations have left the window
            Instant oldestToExpire = sent.get(sent.size() - invitationLimits.senderMax());
            throw InvitationRateLimitException.senderLimit(oldestToExpire.plus(invitationLimits.senderWindow()));
        }
    }

    /**
     * One attempt, not retried here: the caller runs it in a transaction and replays it on a concurrent change, so that
     * the characters and the seat are always written together.
     */
    @Override
    public Adventure quitAdventure(UUID adventureId, UUID accountId, Map<UUID, String> deletions) {
        Account account = currentAccountService.getAccountById(accountId);

        Adventure adventure = adventureFinderApi.findById(adventureId)
                .orElseThrow(AdventureNotFoundException::new);

        if (!adventure.getPlayers().contains(account.getId())) {
            throw new PlayerNotInAdventureException();
        }

        List<GameCharacter> mine = gameCharacters.findByAdventureId(adventureId).stream()
                .filter(character -> account.getId().equals(character.getOwner().getId()))
                .toList();
        Map<UUID, GameCharacter> byId = mine.stream().collect(Collectors.toMap(GameCharacter::getId, Function.identity()));

        // Everything is checked before anything is written: a wrong name or a full reserve refuses the whole quit
        Map<UUID, String> requested = deletions == null ? Map.of() : deletions;
        requested.forEach((characterId, confirmation) -> {
            GameCharacter character = byId.get(characterId);
            if (character == null) {
                throw new DeletionNotConfirmedException();
            }
            DeletionConfirmation.requireName(character.getName(), confirmation);
        });

        List<GameCharacter> moving = mine.stream().filter(character -> !requested.containsKey(character.getId())).toList();
        if (!moving.isEmpty()) {
            // "used" counts all the arrivals but the last one: the reserve must still have room for that last one
            entitlementApi.verifyAccountLimit(account.getId(), PlanLimit.CHARACTERS_WITHOUT_ADVENTURE,
                    gameCharacters.countByOwnerIdWithoutAdventure(account.getId()) + moving.size() - 1);
        }

        mine.stream().filter(character -> requested.containsKey(character.getId())).forEach(this::deleteCharacter);
        moving.forEach(character -> gameCharacters.save(character.withAdventure(null).withActive(false)));

        List<UUID> players = new ArrayList<>(adventure.getPlayers());
        players.remove(account.getId());
        Adventure left = spi.save(adventure.withPlayers(players));

        // The chat and the WebRTC signals already open for this account must stop as well
        accessRevocation.participationEnded(account.getId(), adventureId);

        return left;
    }

    private void deleteCharacter(GameCharacter character) {
        gameCharacters.deleteById(character.getId());
        audit.deletion("character", character.getOwner().getId(), character.getId());
        if (character.getPicturePath() != null) {
            minioSpi.deleteAfterCommit(character.getPicturePath());
        }
    }

    /**
     * Two players who join at the same moment both read the same list of players: one of the two writes is refused.
     * The refused one is replayed from a fresh read, which makes it see the other player (and the capacity).
     */
    private Adventure retryOnConcurrentChange(Supplier<Adventure> attempt) {
        for (int remaining = MAX_ATTEMPTS - 1; ; remaining--) {
            try {
                return attempt.get();
            } catch (AdventureConcurrentModificationException e) {
                if (remaining == 0) {
                    throw e;
                }
            }
        }
    }


}
