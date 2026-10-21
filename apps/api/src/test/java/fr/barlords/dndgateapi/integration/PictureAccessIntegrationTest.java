package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.ByteBuffer;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/** The pictures are not public: they are served by the API, to the people who may see the character. */
class PictureAccessIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount master;

    private TestAccount player;

    private TestAccount stranger;

    private UUID adventureId;

    @BeforeEach
    void fixture() throws Exception {
        reset(minioSpi);
        master = registerAndLogin();
        player = registerAndLogin();
        stranger = registerAndLogin();
        adventureId = createAdventure(master, "PUBLIC");
        Assertions.assertEquals(200, call(HttpMethod.POST, "/adventures/" + adventureId + "/join", player.token(), null).getStatusCode().value());
    }

    private UUID character(TestAccount owner, byte[] picture, MediaType declaredType) throws Exception {
        ResponseEntity<String> created = createCharacter(owner, adventureId, "Gandalf", picture, declaredType);
        Assertions.assertEquals(201, created.getStatusCode().value(), created.getBody());
        UUID id = UUID.fromString(body(created).get("id").asString());
        String path = jdbc.queryForObject("select picture_path from \"character\" where id = ?", String.class, id);
        when(minioSpi.downloadFile(path)).thenAnswer(invocation -> ByteBuffer.wrap(picture));
        return id;
    }

    private ResponseEntity<byte[]> picture(TestAccount account, UUID characterId) {
        return client().get().uri("/game-characters/" + characterId + "/picture")
                .headers(headers -> {
                    if (account != null) {
                        headers.setBearerAuth(account.token());
                    }
                })
                .retrieve().toEntity(byte[].class);
    }

    // ---- who can read ----

    @Test
    void the_owner_and_the_participants_read_the_picture() throws Exception {
        UUID characterId = character(player, PNG, MediaType.IMAGE_PNG);

        for (TestAccount reader : new TestAccount[]{player, master}) {
            ResponseEntity<byte[]> response = picture(reader, characterId);

            Assertions.assertEquals(200, response.getStatusCode().value());
            Assertions.assertArrayEquals(PNG, response.getBody());
            Assertions.assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        }
    }

    @Test
    void a_stranger_and_an_anonymous_visitor_cannot_read_it() throws Exception {
        UUID characterId = character(player, PNG, MediaType.IMAGE_PNG);

        ResponseEntity<byte[]> asStranger = picture(stranger, characterId);

        Assertions.assertEquals(403, asStranger.getStatusCode().value());
        Assertions.assertTrue(asStranger.getBody() == null || !new String(asStranger.getBody()).contains("PNG"));
        Assertions.assertEquals(401, picture(null, characterId).getStatusCode().value());
    }

    @Test
    void a_player_who_left_the_adventure_cannot_read_it_any_more() throws Exception {
        UUID characterId = character(player, PNG, MediaType.IMAGE_PNG);
        Assertions.assertEquals(200, call(HttpMethod.DELETE, "/adventures/" + adventureId + "/players/me", player.token(), null).getStatusCode().value());

        Assertions.assertEquals(200, picture(player, characterId).getStatusCode().value(), "the owner of the character keeps reading it");
        Assertions.assertEquals(403, picture(stranger, characterId).getStatusCode().value());
    }

    @Test
    void an_unknown_character_is_a_not_found() {
        Assertions.assertEquals(404, picture(master, UUID.randomUUID()).getStatusCode().value());
    }

    // ---- what is served ----

    @Test
    void a_jpeg_is_served_as_a_jpeg() throws Exception {
        UUID characterId = character(player, JPEG, MediaType.IMAGE_JPEG);

        ResponseEntity<byte[]> response = picture(master, characterId);

        Assertions.assertEquals(MediaType.IMAGE_JPEG, response.getHeaders().getContentType());
        Assertions.assertArrayEquals(JPEG, response.getBody());
    }

    @Test
    void the_picture_is_cached_only_by_the_browser_of_the_reader_and_never_run() throws Exception {
        UUID characterId = character(player, PNG, MediaType.IMAGE_PNG);

        HttpHeaders headers = picture(player, characterId).getHeaders();

        Assertions.assertTrue(headers.getCacheControl().contains("private"), headers.getCacheControl());
        Assertions.assertEquals("inline", headers.getFirst(HttpHeaders.CONTENT_DISPOSITION));
        Assertions.assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
    }

    @Test
    void a_storage_failure_is_a_not_found_and_not_a_server_error_that_tells_more() throws Exception {
        UUID characterId = character(player, PNG, MediaType.IMAGE_PNG);
        when(minioSpi.downloadFile(anyString())).thenThrow(new IllegalStateException("minio is down"));

        ResponseEntity<byte[]> response = picture(player, characterId);

        Assertions.assertEquals(404, response.getStatusCode().value());
        Assertions.assertTrue(response.getBody() == null || !new String(response.getBody()).contains("minio"));
    }

    // ---- what a character says about its picture ----

    @Test
    void a_character_gives_the_route_of_its_picture_and_not_the_internal_path() throws Exception {
        UUID characterId = character(player, PNG, MediaType.IMAGE_PNG);

        var node = body(call(HttpMethod.GET, "/game-characters/" + characterId, master.token(), null));

        Assertions.assertEquals("/game-characters/" + characterId + "/picture", node.get("pictureUrl").asString());
        Assertions.assertFalse(node.has("picturePath"));
        Assertions.assertFalse(node.toString().contains("account/" + player.id()));
    }
}
