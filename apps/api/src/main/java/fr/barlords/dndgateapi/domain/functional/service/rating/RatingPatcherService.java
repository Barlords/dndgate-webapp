package fr.barlords.dndgateapi.domain.functional.service.rating;

import fr.barlords.dndgateapi.domain.functional.exception.rating.RatingNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.rating.RatingOwnerException;
import fr.barlords.dndgateapi.domain.functional.model.Rating;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.rating.RatingPatcherApi;
import fr.barlords.dndgateapi.domain.port.out.RatingPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class RatingPatcherService implements RatingPatcherApi {

    private final RatingPersistenceSpi spi;

    private final CurrentAccountService currentAccountService;

    @Override
    public Rating patch(UUID accountId, Rating rating) {
        Account account = currentAccountService.getAccountById(accountId);

        Rating oldRating = spi.findById(rating.getId())
                .orElseThrow(RatingNotFoundException::new);

        if (oldRating.getEmitterId().equals(account.getId())) {
            return spi.save(oldRating.withRate(rating.getRate()));
        }
        throw new RatingOwnerException();
    }
}
