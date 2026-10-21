package fr.barlords.dndgateapi.domain.functional.service.adventure;

import fr.barlords.dndgateapi.domain.port.out.AuditSpi;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureInProgressException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.service.DeletionConfirmation;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureDeleterApi;
import fr.barlords.dndgateapi.domain.port.out.AdventurePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.AvailabilityPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MessagePersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import fr.barlords.dndgateapi.domain.port.out.PlanningPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Not transactional by itself: the caller wraps it in a transaction so that an adventure is never
 * left half deleted.
 */
@Slf4j
@RequiredArgsConstructor
public class AdventureDeleterService implements AdventureDeleterApi {

    private final AdventurePersistenceSpi spi;

    private final AdventureOwnerVerifierService adventureOwnerVerifierService;

    private final GameCharacterPersistenceSpi gameCharacters;

    private final MessagePersistenceSpi messages;

    private final PlanningPersistenceSpi plannings;

    private final AvailabilityPersistenceSpi availabilities;

    private final MinioSpi minioSpi;

    private final AuditSpi audit;

    @Override
    public void deleteById(UUID accountId, UUID id, String confirmation) {
        Adventure adventure = adventureOwnerVerifierService.verify(accountId, id);

        DeletionConfirmation.requireName(adventure.getName(), confirmation);

        if (AdventureStatus.ONGOING.equals(adventure.getStatus())) {
            throw new AdventureInProgressException();
        }

        List<String> pictures = gameCharacters.findByAdventureId(id).stream()
                .map(GameCharacter::getPicturePath)
                .filter(Objects::nonNull)
                .toList();

        plannings.findByAdventureId(id).ifPresent(planning -> {
            availabilities.deleteByPlanningId(planning.getId());
            plannings.deleteById(planning.getId());
        });
        messages.deleteByAdventureId(id);
        spi.deleteById(id);
        audit.deletion("adventure", accountId, id);

        deletePictures(pictures);
    }

    /** After the data: a picture that cannot be removed must not bring back an adventure that is gone. */
    private void deletePictures(List<String> pictures) {
        for (String picture : pictures) {
            try {
                minioSpi.deleteFile(picture);
            } catch (Exception e) {
                log.warn("The picture {} of a deleted adventure could not be removed", picture, e);
            }
        }
    }

}
