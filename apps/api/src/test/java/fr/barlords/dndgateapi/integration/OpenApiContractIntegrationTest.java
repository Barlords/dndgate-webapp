package fr.barlords.dndgateapi.integration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * {@code contracts/openapi.yaml} is the contract the Angular client is generated from. It is the API as it really
 * is: this test fails when the file and the code drift apart, and checks that what the contract says about security
 * is what the API does.
 * <p>
 * After a change of routes or DTOs: {@code ./mvnw test -Dtest=OpenApiContractIntegrationTest -Dopenapi.update=true}
 * rewrites the file; the diff is then reviewed like code.
 */
class OpenApiContractIntegrationTest extends ApiIntegrationTestSupport {

    private static final Path CONTRACT = Path.of("..", "..", "contracts", "openapi.yaml");

    private static final List<String> METHODS = List.of("get", "post", "put", "patch", "delete");

    private JsonNode spec() {
        ResponseEntity<String> response = call(HttpMethod.GET, "/v3/api-docs", null, null);
        Assertions.assertEquals(200, response.getStatusCode().value(), response.getBody());
        return body(response);
    }

    private record Operation(String method, String path, JsonNode node) {
        String label() {
            return method.toUpperCase() + " " + path;
        }
    }

    private List<Operation> operations(JsonNode spec) {
        List<Operation> operations = new ArrayList<>();
        spec.get("paths").properties().forEach(path -> METHODS.forEach(method -> {
            if (path.getValue().has(method)) {
                operations.add(new Operation(method, path.getKey(), path.getValue().get(method)));
            }
        }));
        return operations;
    }

    private boolean isSecured(JsonNode spec, Operation operation) {
        JsonNode own = operation.node().get("security");
        JsonNode effective = own != null ? own : spec.get("security");
        return effective != null && effective.size() > 0;
    }

    // ================= the file is the API =================

    @Test
    void the_committed_contract_is_the_api_as_it_is() throws IOException {
        String served = call(HttpMethod.GET, "/v3/api-docs.yaml", null, null).getBody();
        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(CONTRACT, served, StandardCharsets.UTF_8);
        }

        Assertions.assertTrue(Files.exists(CONTRACT), "contracts/openapi.yaml is missing: run with -Dopenapi.update=true");
        Object committed = new Yaml().load(Files.readString(CONTRACT, StandardCharsets.UTF_8));
        Assertions.assertEquals(committed, new Yaml().load(served),
                "contracts/openapi.yaml is out of date: ./mvnw test -Dtest=OpenApiContractIntegrationTest -Dopenapi.update=true");
    }

    // ================= what a generated client needs =================

    @Test
    void the_contract_says_who_it_describes() {
        JsonNode spec = spec();

        Assertions.assertTrue(spec.get("openapi").asString().startsWith("3."));
        Assertions.assertEquals("DnDGate API", spec.get("info").get("title").asString());
        Assertions.assertFalse(spec.get("info").get("version").asString().isBlank());
        Assertions.assertTrue(spec.get("servers").get(0).get("url").asString().startsWith("/"),
                "a relative server: the host is the one the client is served from");
    }

    @Test
    void every_operation_has_a_unique_readable_id() {
        List<Operation> operations = operations(spec());
        TreeSet<String> seen = new TreeSet<>();
        TreeSet<String> problems = new TreeSet<>();

        for (Operation operation : operations) {
            String id = operation.node().path("operationId").asString("");
            // springdoc adds "_1", "_2" to a name used twice: a generated client would call it getX_1
            if (id.isBlank() || id.matches(".*_\\d+") || !seen.add(id)) {
                problems.add(operation.label() + " -> " + id);
            }
        }

        Assertions.assertEquals(new TreeSet<String>(), problems);
        Assertions.assertTrue(operations.size() > 60, "the contract holds the routes: " + operations.size());
    }

    @Test
    void every_operation_has_a_one_line_summary() {
        TreeSet<String> problems = new TreeSet<>();
        for (Operation operation : operations(spec())) {
            String summary = operation.node().path("summary").asString("");
            if (summary.isBlank() || summary.lines().count() > 1 || Character.isLowerCase(summary.charAt(0))) {
                problems.add(operation.label() + " -> '" + summary + "'");
            }
        }

        Assertions.assertEquals(new TreeSet<String>(), problems);
    }

    @Test
    void every_operation_belongs_to_a_named_group() {
        TreeSet<String> problems = new TreeSet<>();
        for (Operation operation : operations(spec())) {
            JsonNode tags = operation.node().get("tags");
            // without @Tag springdoc names the group after the class ("adventure-controller"): ugly generated services
            if (tags == null || tags.size() != 1 || tags.get(0).asString().endsWith("-controller")) {
                problems.add(operation.label() + " -> " + tags);
            }
        }

        Assertions.assertEquals(new TreeSet<String>(), problems);
    }

    @Test
    void errors_are_described_once_and_every_operation_points_to_them() {
        JsonNode spec = spec();
        JsonNode problem = spec.get("components").get("schemas").get("Problem");

        Assertions.assertNotNull(problem, "the problem details schema");
        for (String field : List.of("type", "title", "status", "detail", "instance", "correlationId", "errors")) {
            Assertions.assertTrue(problem.get("properties").has(field), "Problem." + field);
        }

        TreeSet<String> problems = new TreeSet<>();
        for (Operation operation : operations(spec)) {
            JsonNode error = operation.node().path("responses").path("default");
            String ref = error.path("content").path("application/problem+json").path("schema").path("$ref").asString("");
            if (!ref.endsWith("/Problem")) {
                problems.add(operation.label());
            }
        }
        Assertions.assertEquals(new TreeSet<String>(), problems, "operations without the error response");
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asString()));
        return values;
    }

    /** The values of an enum, a JSON null written "null". */
    private static List<String> valuesOrNull(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.isNull() ? "null" : value.asString()));
        return values;
    }

    private static boolean mayBeNull(JsonNode property) {
        return texts(property.path("type")).contains("null");
    }

    @Test
    void the_refusals_of_the_plan_are_described_in_the_problem() {
        JsonNode problem = spec().get("components").get("schemas").get("Problem");
        JsonNode properties = problem.get("properties");

        for (String type : List.of("urn:dndgate:plan-limit-reached", "urn:dndgate:feature-not-included",
                "urn:dndgate:subscription-sync-conflict")) {
            Assertions.assertTrue(problem.get("description").asString().contains(type), type);
        }
        Assertions.assertEquals("string", properties.get("limit").get("type").asString());
        Assertions.assertEquals("integer", properties.get("max").get("type").asString());
        Assertions.assertEquals("string", properties.get("feature").get("type").asString());
        Assertions.assertEquals("string", properties.get("planId").get("type").asString());
        Assertions.assertTrue(mayBeNull(properties.get("upgradePlanId")), "no plan may allow it");
        Assertions.assertEquals(List.of("account", "master"), texts(properties.get("decidedBy").get("enum")));
        Assertions.assertFalse(problem.has("required"), "every field is optional");
    }

    @Test
    void the_answers_say_which_values_and_which_nulls_they_hold() {
        JsonNode schemas = spec().get("components").get("schemas");

        for (String dto : List.of("AccountDto", "PublicAccountDto")) {
            JsonNode badge = schemas.get(dto).get("properties").get("planBadge");
            Assertions.assertTrue(mayBeNull(badge), dto + ".planBadge: null without a badge");
            Assertions.assertEquals(List.of("dungeon-master", "legend", "null"), valuesOrNull(badge.get("enum")), dto + ".planBadge");
        }
        JsonNode planBadge = schemas.get("PlanDto").get("properties").get("badge");
        Assertions.assertTrue(mayBeNull(planBadge), "PlanDto.badge: null without a badge");
        Assertions.assertEquals(List.of("dungeon-master", "legend", "null"), valuesOrNull(planBadge.get("enum")), "PlanDto.badge");
        JsonNode character = schemas.get("GameCharacterDto").get("properties");
        Assertions.assertEquals(List.of("PRIVATE", "PUBLIC"), texts(character.get("visibility").get("enum")));
        Assertions.assertTrue(mayBeNull(character.get("pictureUrl")), "the picture is optional");
        Assertions.assertTrue(mayBeNull(character.get("adventureId")), "a character may live outside any adventure");
        Assertions.assertEquals(1, schemas.get("CheckoutSessionRequest").get("properties").get("planId")
                .get("minLength").asInt());
    }

    @Test
    void no_two_routes_can_match_the_same_url() {
        JsonNode spec = spec();
        TreeSet<String> ambiguous = new TreeSet<>();
        List<Operation> operations = operations(spec);

        for (int i = 0; i < operations.size(); i++) {
            for (int j = i + 1; j < operations.size(); j++) {
                Operation a = operations.get(i);
                Operation b = operations.get(j);
                if (a.method().equals(b.method()) && overlap(a.path(), b.path())) {
                    ambiguous.add(a.label() + " <-> " + b.path());
                }
            }
        }

        Assertions.assertEquals(new TreeSet<String>(), ambiguous);
    }

    private static boolean isVariable(String segment) {
        return segment.startsWith("{");
    }

    /**
     * Two paths overlap when some URL fits both and neither is the more specific one
     * (/a/b/{x} and /a/{y}/c: the URL /a/b/c fits both). /a/b and /a/{x} do not: the literal wins.
     */
    private static boolean overlap(String first, String second) {
        String[] a = first.split("/");
        String[] b = second.split("/");
        if (a.length != b.length) {
            return false;
        }
        boolean aMoreSpecific = true;
        boolean bMoreSpecific = true;
        for (int i = 0; i < a.length; i++) {
            boolean compatible = isVariable(a[i]) || isVariable(b[i]) || a[i].equals(b[i]);
            if (!compatible) {
                return false;
            }
            aMoreSpecific &= !isVariable(a[i]) || isVariable(b[i]);
            bMoreSpecific &= !isVariable(b[i]) || isVariable(a[i]);
        }
        return !aMoreSpecific && !bMoreSpecific;
    }

    @Test
    void the_answers_are_json_not_anything() {
        TreeSet<String> problems = new TreeSet<>();
        for (Operation operation : operations(spec())) {
            operation.node().path("responses").properties().forEach(response -> {
                JsonNode any = response.getValue().path("content").path("*/*");
                // a file (a picture, an export) is binary and keeps whatever type it has; the rest is JSON
                boolean binary = "binary".equals(any.path("schema").path("format").asString(""));
                if (!any.isMissingNode() && !binary) {
                    problems.add(operation.label() + " " + response.getKey());
                }
            });
        }

        Assertions.assertEquals(new TreeSet<String>(), problems, "answers declared as */*: a generated client cannot parse them");
    }

    // ================= the four routes a generated client could not guess =================

    @Test
    void the_character_listing_is_one_operation_with_two_optional_filters() {
        JsonNode list = spec().get("paths").get("/game-characters").get("get");

        Assertions.assertEquals("getGameCharacters", list.get("operationId").asString());
        TreeSet<String> names = new TreeSet<>();
        list.get("parameters").forEach(parameter -> {
            names.add(parameter.get("name").asString());
            Assertions.assertFalse(parameter.path("required").asBoolean(false), parameter.toString());
        });
        Assertions.assertEquals(new TreeSet<>(List.of("adventureId", "ownerId")), names);
    }

    @Test
    void the_spell_book_import_is_a_multipart_file() {
        JsonNode body = spec().get("paths").get("/spellbooks/import-file").get("post").get("requestBody");

        JsonNode schema = body.get("content").get("multipart/form-data").get("schema");
        Assertions.assertEquals("string", schema.get("properties").get("file").get("type").asString());
        Assertions.assertEquals("binary", schema.get("properties").get("file").get("format").asString());
        Assertions.assertEquals("file", schema.get("required").get(0).asString());
    }

    @Test
    void the_picture_is_a_png_or_a_jpeg_file() {
        JsonNode content = spec().get("paths").get("/game-characters/{id}/picture").get("get")
                .get("responses").get("200").get("content");

        for (String type : List.of("image/png", "image/jpeg")) {
            Assertions.assertEquals("string", content.get(type).get("schema").get("type").asString(), type);
            Assertions.assertEquals("binary", content.get(type).get("schema").get("format").asString(), type);
        }
    }

    @Test
    void the_export_is_a_spell_book_file() {
        JsonNode content = spec().get("paths").get("/spellbooks/{id}/export-file").get("get")
                .get("responses").get("200").get("content");

        Assertions.assertTrue(content.get("application/json").get("schema").get("$ref").asString()
                .endsWith("/SpellBookImportExportDto"));
    }

    // ================= what the contract says about security is true =================

    @Test
    void the_token_is_a_bearer_jwt() {
        JsonNode scheme = spec().get("components").get("securitySchemes").get("bearerAuth");

        Assertions.assertEquals("http", scheme.get("type").asString());
        Assertions.assertEquals("bearer", scheme.get("scheme").asString());
        Assertions.assertEquals("JWT", scheme.get("bearerFormat").asString());
    }

    @Test
    void an_operation_asks_for_the_token_exactly_when_the_api_does() {
        JsonNode spec = spec();
        TreeSet<String> wrong = new TreeSet<>();

        for (Operation operation : operations(spec)) {
            String path = Pattern.compile("\\{[^}]+}").matcher(operation.path()).replaceAll(UUID.randomUUID().toString());
            HttpMethod method = HttpMethod.valueOf(operation.method().toUpperCase());
            Object bodyToSend = operation.method().equals("get") || operation.method().equals("delete") ? null : Map.of();

            // no token at all
            int status = call(method, path, null, bodyToSend).getStatusCode().value();

            boolean refusedForNoToken = status == 401;
            if (refusedForNoToken != isSecured(spec, operation)) {
                wrong.add(operation.label() + " (contract says " + (isSecured(spec, operation) ? "secured" : "public")
                        + ", the API answered " + status + ")");
            }
        }

        Assertions.assertEquals(new TreeSet<String>(), wrong);
    }
}
