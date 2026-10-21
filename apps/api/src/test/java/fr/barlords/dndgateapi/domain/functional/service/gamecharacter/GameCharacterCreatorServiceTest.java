package fr.barlords.dndgateapi.domain.functional.service.gamecharacter;

import fr.barlords.dndgateapi.domain.functional.exception.DataNotSaveException;
import fr.barlords.dndgateapi.domain.functional.exception.InvalidPictureException;
import fr.barlords.dndgateapi.domain.functional.exception.adventure.AdventureAccessDeniedException;
import fr.barlords.dndgateapi.domain.functional.model.GameCharacter;
import fr.barlords.dndgateapi.domain.functional.model.account.Account;
import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import fr.barlords.dndgateapi.domain.functional.service.CurrentAccountService;
import fr.barlords.dndgateapi.domain.port.in.adventure.AdventureAccessVerifierApi;
import fr.barlords.dndgateapi.domain.port.out.GameCharacterPersistenceSpi;
import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import io.vavr.control.Either;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameCharacterCreatorServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    @Mock
    private GameCharacterPersistenceSpi spi;

    @Mock
    private AdventureAccessVerifierApi adventureAccessVerifierApi;

    @Mock
    private CurrentAccountService currentAccountService;

    @Mock
    private MinioSpi minioSpi;

    private GameCharacterCreatorService service;

    private final Account player = Account.builder().pseudo("player").email("player@test.fr").build();

    private final Adventure adventure = Adventure.builder().name("Quest").build();

    private final GameCharacter draft = GameCharacter.builder().name("Gandalf").race("Human").build();

    @BeforeEach
    void setUp() {
        service = new GameCharacterCreatorService(spi, adventureAccessVerifierApi, currentAccountService, minioSpi);
        when(currentAccountService.getAccountById(player.getId())).thenReturn(player);
    }

    private void playerIsParticipant() {
        when(adventureAccessVerifierApi.verifyParticipant(player.getId(), adventure.getId())).thenReturn(adventure);
    }

    private GameCharacter create(byte[] picture) {
        return service.create(player.getId(), adventure.getId(), draft, new ByteArrayInputStream(picture));
    }

    @Test
    void the_character_belongs_to_the_caller_and_to_the_adventure_and_keeps_the_picture_path() {
        playerIsParticipant();
        when(spi.save(any(GameCharacter.class))).thenAnswer(i -> Either.right(i.getArguments()[0]));

        GameCharacter created = create(PNG);

        Assertions.assertEquals(player, created.getOwner());
        Assertions.assertEquals(adventure, created.getAdventure());
        Assertions.assertEquals("account/" + player.getId() + "/game_character/" + draft.getId() + ".png", created.getPicturePath());
        Assertions.assertEquals("Gandalf", created.getName());
    }

    @Test
    void the_picture_is_stored_where_the_character_says_with_its_real_content_type() throws Exception {
        playerIsParticipant();
        when(spi.save(any(GameCharacter.class))).thenAnswer(i -> Either.right(i.getArguments()[0]));

        GameCharacter created = create(PNG);

        ArgumentCaptor<InputStream> stored = ArgumentCaptor.forClass(InputStream.class);
        verify(minioSpi).uploadFile(eq("image/png"), stored.capture(), eq(created.getPicturePath()));
        Assertions.assertArrayEquals(PNG, stored.getValue().readAllBytes());
    }

    @Test
    void a_jpeg_gets_a_jpg_extension_and_its_own_content_type() throws Exception {
        playerIsParticipant();
        when(spi.save(any(GameCharacter.class))).thenAnswer(i -> Either.right(i.getArguments()[0]));

        GameCharacter created = create(JPEG);

        Assertions.assertTrue(created.getPicturePath().endsWith(".jpg"), created.getPicturePath());
        verify(minioSpi).uploadFile(eq("image/jpeg"), any(), eq(created.getPicturePath()));
    }

    @Test
    void something_that_is_not_a_picture_is_refused_before_anything_is_stored() throws Exception {
        playerIsParticipant();
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 1, 2};

        for (byte[] content : new byte[][]{html, gif, new byte[0], {(byte) 0x89, 'P'}}) {
            Assertions.assertThrows(InvalidPictureException.class, () -> create(content));
        }

        verify(minioSpi, never()).uploadFile(any(), any(), any());
        verify(spi, never()).save(any());
    }

    @Test
    void a_picture_over_five_megabytes_is_refused() throws Exception {
        playerIsParticipant();
        byte[] huge = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, huge, 0, PNG.length);

        Assertions.assertThrows(InvalidPictureException.class, () -> create(huge));

        verify(minioSpi, never()).uploadFile(any(), any(), any());
    }

    @Test
    void a_picture_of_exactly_five_megabytes_is_accepted() {
        playerIsParticipant();
        when(spi.save(any(GameCharacter.class))).thenAnswer(i -> Either.right(i.getArguments()[0]));
        byte[] limit = new byte[5 * 1024 * 1024];
        System.arraycopy(PNG, 0, limit, 0, PNG.length);

        Assertions.assertDoesNotThrow(() -> create(limit));
    }

    @Test
    void an_unreadable_stream_is_an_invalid_picture() throws Exception {
        playerIsParticipant();
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("broken");
            }
        };

        Assertions.assertThrows(InvalidPictureException.class,
                () -> service.create(player.getId(), adventure.getId(), draft, broken));
    }

    @Test
    void someone_who_does_not_take_part_in_the_adventure_creates_nothing_and_stores_nothing() throws Exception {
        doThrow(new AdventureAccessDeniedException()).when(adventureAccessVerifierApi)
                .verifyParticipant(player.getId(), adventure.getId());

        Assertions.assertThrows(AdventureAccessDeniedException.class, () -> create(PNG));

        verify(minioSpi, never()).uploadFile(any(), any(), any());
        verify(spi, never()).save(any());
    }

    @Test
    void a_storage_failure_creates_no_character() throws Exception {
        playerIsParticipant();
        doThrow(new IllegalStateException("minio down")).when(minioSpi).uploadFile(any(), any(), any());

        Assertions.assertThrows(IllegalStateException.class, () -> create(PNG));

        verify(spi, never()).save(any());
    }

    @Test
    void a_failed_save_is_reported() {
        playerIsParticipant();
        when(spi.save(any(GameCharacter.class))).thenReturn(Either.left(null));

        Assertions.assertThrows(DataNotSaveException.class, () -> create(PNG));
    }
}
