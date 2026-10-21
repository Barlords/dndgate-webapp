package fr.barlords.dndgateapi.domain.functional.service.adventure;

import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureAccessDeniedException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureNotFoundException;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class AdventureAccessVerifierService implements AdventureAccessVerifierApi {

    private final AdventurePersistenceSpi spi;

    @Override
    public Adventure verifyParticipant(UUID accountId, UUID adventureId) {
        Adventure adventure = spi.findById(adventureId)
                .orElseThrow(AdventureNotFoundException::new);

        if (!adventure.isParticipant(accountId)) {
            throw new AdventureAccessDeniedException();
        }

        return adventure;
    }

}
