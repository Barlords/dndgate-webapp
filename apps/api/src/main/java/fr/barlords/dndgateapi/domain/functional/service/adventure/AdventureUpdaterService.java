package fr.barlords.dndgateapi.domain.functional.service.adventure;

import java.util.Objects;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureConcurrentModificationException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotJoinableException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureFullException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.PlayerNotInAdventureException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.PlayersAlreadyInAdventureException;
import fr.barlords.dndgateapi.domain.functional.exception.account.AccountNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureFinderApi;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureUpdaterApi;
import fr.barlords.dndgateapi.domain.port.in.account.AccountFinderApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.EmailingSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
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

    @Override
    public Adventure update(UUID accountId, Adventure adventure) {
        Adventure previousAdventure = adventureOwnerVerifierService.verify(accountId, adventure.getId());

        Adventure patchedAdventure = previousAdventure
                .withName(Objects.requireNonNullElse(adventure.getName(), previousAdventure.getName()))
                .withStatus(Objects.requireNonNullElse(adventure.getStatus(), previousAdventure.getStatus()))
                .withDescription(Objects.requireNonNullElse(adventure.getDescription(), previousAdventure.getDescription()))
                .withVisibility(Objects.requireNonNullElse(adventure.getVisibility(), previousAdventure.getVisibility()))
                .withPostalCode(Objects.requireNonNullElse(adventure.getPostalCode(), previousAdventure.getPostalCode()))
                .withCity(Objects.requireNonNullElse(adventure.getCity(), previousAdventure.getCity()))
                .withMaxPlayer(Objects.requireNonNullElse(adventure.getMaxPlayer(), previousAdventure.getMaxPlayer()));

        return spi.save(patchedAdventure)
                .getOrElseThrow(DataNotSaveException::new);
    }

    @Override
    public Adventure updateVisibility(UUID accountId, UUID id, AdventureVisibility visibility) {
        Adventure adventure = adventureOwnerVerifierService.verify(accountId, id);

        return spi.save(adventure.withVisibility(visibility))
                .getOrElseThrow(DataNotSaveException::new);
    }

    @Override
    public Adventure updateStatus(UUID accountId, UUID id, AdventureStatus status) {
        Adventure adventure = adventureOwnerVerifierService.verify(accountId, id);

        return spi.save(adventure.withStatus(status))
                .getOrElseThrow(DataNotSaveException::new);
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

        return spi.save(joined)
                .getOrElseThrow(DataNotSaveException::new);
    }

    @Override
    public void sendInvitationCode(UUID accountId, UUID adventureId, String targetPseudo) {
        Account senderAccount = currentAccountService.getAccountById(accountId);

        Adventure adventure = adventureOwnerVerifierService.verify(accountId, adventureId);

        Account targetedAccount = accountFinderApi.findByPseudo(targetPseudo.trim())
                .filter(found -> !found.isAnonymized())
                .orElseThrow(AccountNotFoundException::new);

        emailingSpi.sendInvitationCodeEmail(senderAccount, targetedAccount, adventure.getInvitationCode());
    }

    @Override
    public Adventure quitAdventure(UUID adventureId, UUID accountId) {
        Account account = currentAccountService.getAccountById(accountId);

        return retryOnConcurrentChange(() -> {
            Adventure adventure = adventureFinderApi.findById(adventureId)
                    .orElseThrow(AdventureNotFoundException::new);

            if (!adventure.getPlayers().contains(account.getId())) {
                throw new PlayerNotInAdventureException();
            }

            List<UUID> players = new ArrayList<>(adventure.getPlayers());
            players.remove(account.getId());

            return spi.save(adventure.withPlayers(players))
                    .getOrElseThrow(DataNotSaveException::new);
        });
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
