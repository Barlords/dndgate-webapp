package fr.barlords.dndgateapi.domain.functional.service.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.model.GameCharacterPicture;
import fr.barlords.dndgateapi.domain.functional.model.PictureFormat;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import java.nio.ByteBuffer;

import fr.barlords.dndgateapi.domain.functional.exception.gamecharacter.GameCharacterNotFoundException;
import fr.barlords.dndgateapi.domain.functional.exception.gamecharacter.GameCharacterOwnerException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.gamecharacter.GameCharacterFinderApi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class GameCharacterFinderService implements GameCharacterFinderApi {

    private final GameCharacterPersistenceSpi spi;

    private final AdventureAccessVerifierApi adventureAccessVerifierApi;

    private final MinioSpi minioSpi;

    @Override
    public Optional<GameCharacter> findById(UUID accountId, UUID id) {
        return spi.findById(id).map(character -> {
            if (!accountId.equals(character.getOwner().getId())) {
                adventureAccessVerifierApi.verifyParticipant(accountId, character.getAdventure().getId());
            }
            return character;
        });
    }

    @Override
    public List<GameCharacter> findByOwnerId(UUID accountId, UUID ownerId) {
        if (!accountId.equals(ownerId)) {
            throw new GameCharacterOwnerException();
        }

        return spi.findByOwnerId(ownerId);
    }

    @Override
    public List<GameCharacter> findByAdventureId(UUID accountId, UUID adventureId) {
        adventureAccessVerifierApi.verifyParticipant(accountId, adventureId);

        return spi.findByAdventureId(adventureId);
    }

    @Override
    public List<GameCharacter> findMyCharacter(UUID accountId) {
        return spi.findByOwnerId(accountId);
    }

    @Override
    public GameCharacterPicture findPicture(UUID accountId, UUID id) {
        GameCharacter character = findById(accountId, id).orElseThrow(GameCharacterNotFoundException::new);

        PictureFormat format = PictureFormat.fromPath(character.getPicturePath()).orElseThrow(GameCharacterNotFoundException::new);

        try {
            ByteBuffer content = minioSpi.downloadFile(character.getPicturePath());
            byte[] bytes = new byte[content.remaining()];
            content.get(bytes);
            return new GameCharacterPicture(bytes, format.contentType());
        } catch (Exception e) {
            log.warn("The picture of the character {} cannot be read", id, e);
            throw new GameCharacterNotFoundException();
        }
    }

}
