package fr.barlords.dndgateapi.integration;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Import validates the file like the creation routes do; export sends a real attachment header.
 */
class SpellBookImportExportIntegrationTest extends ApiIntegrationTestSupport {

    private ResponseEntity<String> importFile(TestAccount account, String json) {
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "book.json";
            }
        });
        return client().post().uri("/spellbooks/import-file")
                .headers(headers -> headers.setBearerAuth(account.token()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve().toEntity(String.class);
    }

    private void assertInvalid(ResponseEntity<String> response) {
        Assertions.assertEquals(400, response.getStatusCode().value(), response.getBody());
        Assertions.assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(response.getHeaders().getContentType()));
        Assertions.assertTrue(body(response).get("errors").size() >= 1, response.getBody());
    }

    @Test
    void a_file_without_a_name_is_a_400_with_the_errors() {
        TestAccount account = registerAndLogin();
        ResponseEntity<String> response = importFile(account, "{\"description\":\"d\",\"spellList\":[]}");
        assertInvalid(response);
        Assertions.assertEquals("name", body(response).get("errors").get(0).get("field").asText());
    }

    @Test
    void a_name_of_256_characters_is_a_400() {
        TestAccount account = registerAndLogin();
        assertInvalid(importFile(account, "{\"name\":\"" + "n".repeat(256) + "\",\"description\":\"d\",\"spellList\":[]}"));
    }

    @Test
    void a_spell_of_256_characters_is_a_400() {
        TestAccount account = registerAndLogin();
        assertInvalid(importFile(account, "{\"name\":\"b\",\"description\":\"d\",\"spellList\":[\"" + "s".repeat(256) + "\"]}"));
    }

    @Test
    void a_blank_spell_is_a_400() {
        TestAccount account = registerAndLogin();
        ResponseEntity<String> response = importFile(account, "{\"name\":\"b\",\"description\":\"d\",\"spellList\":[\"  \"]}");
        assertInvalid(response);
        Assertions.assertEquals("spellList[]", body(response).get("errors").get(0).get("field").asText());
    }

    @Test
    void a_file_that_is_not_json_is_a_400() {
        TestAccount account = registerAndLogin();
        ResponseEntity<String> response = importFile(account, "this is not json");
        Assertions.assertEquals(400, response.getStatusCode().value(), response.getBody());
        Assertions.assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(response.getHeaders().getContentType()));
    }

    @Test
    void a_valid_file_is_imported_and_exported_as_an_attachment() {
        TestAccount account = registerAndLogin();
        ResponseEntity<String> created = importFile(account,
                "{\"name\":\"Imported\",\"description\":\"d\",\"spellList\":[\"fireball\"]}");
        Assertions.assertEquals(201, created.getStatusCode().value(), created.getBody());
        JsonNode book = body(created);
        Assertions.assertEquals("Imported", book.get("name").asText());

        ResponseEntity<String> exported = call(HttpMethod.GET,
                "/spellbooks/" + book.get("id").asText() + "/export-file", account.token(), null);

        Assertions.assertEquals(200, exported.getStatusCode().value());
        Assertions.assertEquals("attachment; filename=\"book.json\"",
                exported.getHeaders().getFirst("Content-Disposition"));
        Assertions.assertEquals("fireball", body(exported).get("spellList").get(0).asText());
    }

    @Test
    void a_spell_book_file_is_imported_as_multipart() {
        TestAccount account = registerAndLogin();

        ResponseEntity<String> response = importFile(account, "{\"name\":\"Multipart\",\"description\":\"d\",\"spellList\":[]}");

        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
    }

    @Test
    void an_import_without_a_file_part_is_a_400() {
        TestAccount account = registerAndLogin();
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("other", "value");

        ResponseEntity<String> response = client().post().uri("/spellbooks/import-file")
                .headers(headers -> headers.setBearerAuth(account.token()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve().toEntity(String.class);

        Assertions.assertEquals(400, response.getStatusCode().value(), response.getBody());
        Assertions.assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(response.getHeaders().getContentType()));
    }

    @Test
    void the_export_contains_the_ruleset() {
        TestAccount account = registerAndLogin();
        String id = body(importFile(account,
                "{\"name\":\"B\",\"ruleset\":\"dnd5e-2024\",\"spellList\":[]}")).get("id").asText();

        ResponseEntity<String> exported = call(HttpMethod.GET, "/spellbooks/" + id + "/export-file", account.token(), null);

        Assertions.assertEquals("dnd5e-2024", body(exported).get("ruleset").asText());
    }

    @Test
    void an_import_keeps_the_ruleset_of_the_file() {
        TestAccount account = registerAndLogin();

        ResponseEntity<String> response = importFile(account,
                "{\"name\":\"B\",\"ruleset\":\"dnd5e-2024\",\"spellList\":[]}");

        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals("dnd5e-2024", body(response).get("ruleset").asText());
    }

    @Test
    void an_import_without_ruleset_is_dnd5e_2014() {
        TestAccount account = registerAndLogin();

        ResponseEntity<String> response = importFile(account, "{\"name\":\"B\",\"spellList\":[]}");

        Assertions.assertEquals(201, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals("dnd5e-2014", body(response).get("ruleset").asText());
    }

    @Test
    void an_import_with_a_ruleset_that_is_not_an_identifier_is_a_400() {
        TestAccount account = registerAndLogin();

        assertInvalid(importFile(account, "{\"name\":\"B\",\"ruleset\":\"DnD 5e\",\"spellList\":[]}"));
    }
}
