package fr.barlords.dndgateapi.domain.functional.service.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.exception.QuantityLimitExceededException;
import fr.barlords.dndgateapi.domain.functional.exception.InvalidPictureException;
import fr.barlords.dndgateapi.domain.functional.model.PictureFormat;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.in.gamecharacter.GameCharacterCreatorApi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import fr.barlords.dndgateapi.domain.functional.model.QuantityLimits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class GameCharacterCreatorService implements GameCharacterCreatorApi {

    private final GameCharacterPersistenceSpi spi;

    private final AdventureAccessVerifierApi adventureAccessVerifierApi;

    private final CurrentAccountService currentAccountService;

    private final MinioSpi minioSpi;

    private final QuantityLimits limits;

    private static final int MAX_PICTURE_BYTES = 5 * 1024 * 1024;

    private static byte[] readPicture(InputStream pictureInputStream) {
        try {
            byte[] picture = pictureInputStream.readNBytes(MAX_PICTURE_BYTES + 1);
            if (picture.length == 0 || picture.length > MAX_PICTURE_BYTES) {
                throw new InvalidPictureException();
            }
            return picture;
        } catch (IOException e) {
            throw new InvalidPictureException();
        }
    }

    @Override
    public GameCharacter create(UUID accountId, UUID adventureId, GameCharacter gameCharacter, InputStream pictureInputStream) {
        Account account = currentAccountService.getAccountById(accountId);

        Adventure adventure = adventureId == null ? null : adventureAccessVerifierApi.verifyParticipant(accountId, adventureId);

        // Checked before the picture is read and stored: a refused character must not leave a file behind
        verifyLimit(account.getId(), adventureId);

        // The format is recognised by the content of the file: what the client declares is not trusted
        byte[] picture = readPicture(pictureInputStream);
        PictureFormat format = PictureFormat.detect(picture).orElseThrow(InvalidPictureException::new);

        String picturePath = "account/" + account.getId() + "/game_character/" + gameCharacter.getId() + "." + format.extension();

        try {
            minioSpi.uploadFile(format.contentType(), new ByteArrayInputStream(picture), picturePath);
        } catch (Exception e) {
            throw new IllegalStateException("The file cannot be stored", e);
        }

        try {
            return spi.save(gameCharacter
                    .withOwner(account)
                    .withAdventure(adventure)
                    .withPicturePath(picturePath)
            );
        } catch (RuntimeException e) {
            removeOrphanPicture(picturePath);
            throw e;
        }
    }

    private void verifyLimit(UUID ownerId, UUID adventureId) {
        if (adventureId == null) {
            if (spi.countByOwnerIdWithoutAdventure(ownerId) >= limits.charactersWithoutAdventurePerAccount()) {
                throw QuantityLimitExceededException.charactersWithoutAdventure(limits.charactersWithoutAdventurePerAccount());
            }
        } else if (spi.countByOwnerIdAndAdventureId(ownerId, adventureId) >= limits.charactersPerPlayerAndAdventure()) {
            throw QuantityLimitExceededException.characters(limits.charactersPerPlayerAndAdventure());
        }
    }

    /** Best effort: the character was not saved, so nothing refers to the uploaded picture. */
    private void removeOrphanPicture(String picturePath) {
        try {
            minioSpi.deleteFile(picturePath);
        } catch (Exception e) {
            log.warn("The picture {} of a character that could not be saved was left behind", picturePath, e);
        }
    }

}
