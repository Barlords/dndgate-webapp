package fr.barlords.dndgateapi.domain.functional.service.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
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

        Adventure adventure = adventureAccessVerifierApi.verifyParticipant(accountId, adventureId);

        // The format is recognised by the content of the file: what the client declares is not trusted
        byte[] picture = readPicture(pictureInputStream);
        PictureFormat format = PictureFormat.detect(picture).orElseThrow(InvalidPictureException::new);

        String picturePath = "account/" + account.getId() + "/game_character/" + gameCharacter.getId() + "." + format.extension();

        try {
            minioSpi.uploadFile(format.contentType(), new ByteArrayInputStream(picture), picturePath);
        } catch (Exception e) {
            throw new IllegalStateException("The file cannot be stored", e);
        }

        return spi.save(gameCharacter
                        .withOwner(account)
                        .withAdventure(adventure)
                        .withPicturePath(picturePath)
                ).getOrElseThrow(DataNotSaveException::new);
    }

}
