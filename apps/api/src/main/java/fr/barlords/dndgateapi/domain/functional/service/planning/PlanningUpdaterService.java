package fr.barlords.dndgateapi.domain.functional.service.planning;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.model.planning.Planning;
import fr.barlords.dndgateapi.domain.functional.service.adventure.AdventureOwnerVerifierService;
import fr.barlords.dndgateapi.domain.port.in.planning.PlanningUpdaterApi;
import fr.barlords.dndgateapi.domain.port.out.PlanningPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class PlanningUpdaterService implements PlanningUpdaterApi {

    private final PlanningPersistenceSpi spi;

    private final AdventureOwnerVerifierService adventureOwnerVerifierService;

    @Override
    public Planning createOrUpdate(UUID accountId, Planning planning) {
        adventureOwnerVerifierService.verify(accountId, planning.getAdventureId());

        Planning toSave = spi.findByAdventureId(planning.getAdventureId())
                .map(existing -> existing.withPropositionDates(planning.getPropositionDates()))
                .orElse(planning);

        return spi.save(toSave).getOrElseThrow(DataNotSaveException::new);
    }

}
