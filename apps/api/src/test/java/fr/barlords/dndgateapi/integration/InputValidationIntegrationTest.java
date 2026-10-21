package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

class InputValidationIntegrationTest extends ApiIntegrationTestSupport {

    private TestAccount master;

    private UUID adventureId;

    @BeforeEach
    void fixture() {
        reset(minioSpi);
        master = registerAndLogin();
        adventureId = createAdventure(master, "PUBLIC");
    }

    private int status(HttpMethod method, String path, Object body) {
        return call(method, path, master.token(), body).getStatusCode().value();
    }

    // ---- the routes that were not validated ----

    @Test
    void a_planning_needs_between_one_and_a_hundred_dates() {
        List<LocalDate> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add(LocalDate.of(2027, 1, 1).plusDays(i));
        }
        List<LocalDate> withNull = Arrays.asList(LocalDate.of(2027, 1, 1), null);

        Assertions.assertEquals(400, status(HttpMethod.PUT, "/planning", Map.of("adventureId", adventureId.toString(), "propositionDates", List.of())));
        Assertions.assertEquals(400, status(HttpMethod.PUT, "/planning", Map.of("adventureId", adventureId.toString(), "propositionDates", tooMany)));
        Assertions.assertEquals(400, status(HttpMethod.PUT, "/planning", Map.of("adventureId", adventureId.toString(), "propositionDates", withNull)));
        Assertions.assertEquals(400, status(HttpMethod.PUT, "/planning", Map.of("propositionDates", List.of("2027-01-01"))));
        Assertions.assertEquals(400, status(HttpMethod.POST, "/planning", Map.of("adventureId", adventureId.toString(), "propositionDates", tooMany)));
        Assertions.assertEquals(201, status(HttpMethod.PUT, "/planning",
                Map.of("adventureId", adventureId.toString(), "propositionDates", List.of("2027-01-01", "2027-01-02"))));
    }

    @Test
    void a_availability_is_validated_like_a_planning() {
        UUID planningId = UUID.randomUUID();

        Assertions.assertEquals(400, status(HttpMethod.PUT, "/planning/availability", Map.of("planningId", planningId.toString(), "propositionDates", List.of())));
        Assertions.assertEquals(400, status(HttpMethod.PUT, "/planning/availability", Map.of("propositionDates", List.of("2027-01-01"))));
        Assertions.assertEquals(400, status(HttpMethod.PUT, "/planning/availability", Map.of("planningId", planningId.toString())));
    }

    @Test
    void an_invitation_code_must_be_present_and_reasonable() {
        Map<String, Object> missing = new HashMap<>();

        Assertions.assertEquals(400, status(HttpMethod.POST, "/adventures/invitations/accept", missing));
        Assertions.assertEquals(400, status(HttpMethod.POST, "/adventures/invitations/accept", Map.of("code", "   ")));
        Assertions.assertEquals(400, status(HttpMethod.POST, "/adventures/invitations/accept", Map.of("code", "x".repeat(51))));
        Assertions.assertEquals(404, status(HttpMethod.POST, "/adventures/invitations/accept", Map.of("code", "unknown")));
    }

    @Test
    void a_refresh_token_of_absurd_length_is_refused_before_anything_else() {
        Assertions.assertEquals(400, call(HttpMethod.POST, "/accounts/refresh", null, Map.of("refreshToken", "x".repeat(201))).getStatusCode().value());
    }

    // ---- WebRTC signals ----

    @Test
    void a_webrtc_signal_has_a_known_type_and_a_bounded_content() {
        Map<String, Object> valid = Map.of("type", "offer", "content", "sdp", "adventureId", adventureId.toString());

        Assertions.assertEquals(200, status(HttpMethod.POST, "/webrtc/offer", valid));
        Assertions.assertEquals(400, status(HttpMethod.POST, "/webrtc/offer", Map.of("type", "evil", "content", "sdp", "adventureId", adventureId.toString())));
        Assertions.assertEquals(400, status(HttpMethod.POST, "/webrtc/offer", Map.of("content", "sdp", "adventureId", adventureId.toString())));
        Assertions.assertEquals(400, status(HttpMethod.POST, "/webrtc/offer", Map.of("type", "offer", "adventureId", adventureId.toString())));
        Assertions.assertEquals(400, status(HttpMethod.POST, "/webrtc/offer", Map.of("type", "offer", "content", "sdp")));
        Assertions.assertEquals(400, status(HttpMethod.POST, "/webrtc/answer",
                Map.of("type", "answer", "content", "x".repeat(20001), "adventureId", adventureId.toString())));
        Assertions.assertEquals(200, status(HttpMethod.POST, "/webrtc/candidate",
                Map.of("type", "candidate", "content", "x".repeat(20000), "adventureId", adventureId.toString())));
    }

    @Test
    void a_spell_needs_an_index() {
        UUID bookId = UUID.fromString(call(HttpMethod.POST, "/spellbooks", master.token(),
                Map.of("name", "Book", "description", "d")).getBody().toString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
        String path = "/spellbooks/" + bookId + "/spells";

        Assertions.assertEquals(400, status(HttpMethod.POST, path, Map.of()));
        Assertions.assertEquals(400, status(HttpMethod.POST, path, Map.of("spellIndex", "  ")));
        Assertions.assertEquals(201, status(HttpMethod.POST, path, Map.of("spellIndex", "fireball")));
    }

    // ---- texts made of spaces ----

    @Test
    void a_required_text_cannot_be_made_of_spaces() {
        Assertions.assertEquals(400, status(HttpMethod.POST, "/adventures", Map.of(
                "name", "   ", "description", "A quest", "maxPlayer", 4, "visibility", "PUBLIC", "postalCode", "75001", "city", "Paris")));
        Assertions.assertEquals(400, status(HttpMethod.POST, "/adventures", Map.of(
                "name", "Quest", "description", " \n ", "maxPlayer", 4, "visibility", "PUBLIC", "postalCode", "75001", "city", "Paris")));
        Assertions.assertEquals(400, status(HttpMethod.POST, "/spellbooks", Map.of("name", "  ", "description", "x")));
    }

    @Test
    void an_optional_text_cannot_be_emptied_by_spaces() {
        Assertions.assertEquals(400, status(HttpMethod.PATCH, "/adventures/" + adventureId, Map.of("name", "    ")));
        Assertions.assertEquals(400, status(HttpMethod.PATCH, "/adventures/" + adventureId, Map.of("description", " \t ")));
        Assertions.assertEquals(201, status(HttpMethod.PATCH, "/adventures/" + adventureId, Map.of("name", "Renamed")));
    }

    // ---- pictures ----

    @Test
    void a_png_is_stored_as_a_png() throws Exception {
        Assertions.assertEquals(201, createCharacter(master, adventureId, "Gandalf", PNG, MediaType.IMAGE_PNG).getStatusCode().value());

        verify(minioSpi).uploadFile(eq("image/png"), any(), endsWith(".png"));
    }

    @Test
    void a_jpeg_is_stored_as_a_jpeg_and_not_as_a_png() throws Exception {
        ResponseEntity<String> response = createCharacter(master, adventureId, "Gandalf", JPEG, MediaType.IMAGE_JPEG);

        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
        verify(minioSpi).uploadFile(eq("image/jpeg"), any(), endsWith(".jpg"));
        Assertions.assertTrue(jdbc.queryForObject("select picture_path from \"character\" where id = ?", String.class,
                UUID.fromString(body(response).get("id").asString())).endsWith(".jpg"));
    }

    @Test
    void the_content_decides_not_the_declared_type() throws Exception {
        Assertions.assertEquals(201, createCharacter(master, adventureId, "Gandalf", PNG, MediaType.TEXT_PLAIN).getStatusCode().value());
        verify(minioSpi).uploadFile(eq("image/png"), any(), endsWith(".png"));
    }

    @Test
    void a_file_that_is_not_an_image_is_refused_whatever_it_claims_to_be() throws Exception {
        byte[] script = "<script>alert(1)</script>".getBytes();
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 1, 2};

        for (byte[] content : List.of(script, gif, new byte[0], new byte[]{(byte) 0x89, 'P'})) {
            ResponseEntity<String> response = createCharacter(master, adventureId, "Gandalf", content, MediaType.IMAGE_PNG);
            Assertions.assertEquals(400, response.getStatusCode().value(), response.getBody());
        }
        verify(minioSpi, never()).uploadFile(any(), any(), any());
        Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from \"character\" where adventure_id = ?", Integer.class, adventureId));
    }

    @Test
    void a_picture_beyond_the_limit_is_refused() throws Exception {
        byte[] huge = new byte[5 * 1024 * 1024 + 1024];
        System.arraycopy(PNG, 0, huge, 0, PNG.length);

        ResponseEntity<String> response = createCharacter(master, adventureId, "Gandalf", huge, MediaType.IMAGE_PNG);

        Assertions.assertEquals(413, response.getStatusCode().value(), response.getBody());
        verify(minioSpi, never()).uploadFile(any(), any(), any());
    }

    // ---- the size of a body ----

    @Test
    void a_json_body_beyond_the_limit_is_refused() {
        String huge = "x".repeat(600 * 1024);

        Assertions.assertEquals(413, status(HttpMethod.POST, "/messages",
                Map.of("adventureId", adventureId.toString(), "content", huge)));
        Assertions.assertEquals(413, call(HttpMethod.POST, "/accounts/login", null, Map.of("pseudo", "x", "password", huge)).getStatusCode().value());
    }

    @Test
    void the_longest_legitimate_text_goes_through() {
        // 80 000 characters of 3 bytes: the background of a character is the longest field
        String background = "€".repeat(80000);

        ResponseEntity<String> response = createCharacterWithBackground(background);

        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
    }

    private ResponseEntity<String> createCharacterWithBackground(String background) {
        return createCharacter(master, adventureId, "Gandalf", PNG, MediaType.IMAGE_PNG, background);
    }
}
