package fr.barlords.dndgateapi.domain.functional.service.planning;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureFinderApi;
import fr.barlords.dndgateapi.domain.port.in.planning.PlanningFinderApi;
import fr.barlords.dndgateapi.domain.port.out.PlanningPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class PlanningFinderService implements PlanningFinderApi {

    private final PlanningPersistenceSpi spi;

    private final AdventureFinderApi adventureFinderApi;

    @Override
    public Optional<Planning> findById(UUID accountId, UUID id) {
        return spi.findById(id).map(planning -> {
            requireVisibleAdventure(accountId, planning.getAdventureId());
            return planning;
        });
    }

    @Override
    public Optional<Planning> findByAdventureId(UUID accountId, UUID adventureId) {
        requireVisibleAdventure(accountId, adventureId);

        return spi.findByAdventureId(adventureId);
    }

    private void requireVisibleAdventure(UUID accountId, UUID adventureId) {
        adventureFinderApi.findByIdVisibleTo(accountId, adventureId)
                .orElseThrow(AdventureNotFoundException::new);
    }

}
