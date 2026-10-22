package fr.barlords.dndgateapi.bootstrap.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code contracts/asyncapi.yaml} documents what the API publishes on the WebSocket (STOMP) topics, the part of the
 * API that {@code openapi.yaml} cannot describe. Its payloads point at the OpenAPI schemas, so the shape of a
 * broadcast message is written once.
 */
class AsyncApiContractTest {

    private static final Path ASYNCAPI = Path.of("..", "..", "contracts", "asyncapi.yaml");
    private static final Path OPENAPI = Path.of("..", "..", "contracts", "openapi.yaml");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(Path file) throws IOException {
        Assertions.assertTrue(Files.exists(file), file + " is missing");
        return (Map<String, Object>) new Yaml().load(Files.readString(file));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        Assertions.assertNotNull(value, key + " is missing");
        return (Map<String, Object>) value;
    }

    @Test
    void the_two_adventure_topics_are_documented() throws IOException {
        Map<String, Object> spec = load(ASYNCAPI);
        Assertions.assertTrue(String.valueOf(spec.get("asyncapi")).startsWith("3."));

        Map<String, Object> channels = child(spec, "channels");
        Assertions.assertEquals("/topic/adventure/chat/{adventureId}", child(channels, "adventureChat").get("address"));
        Assertions.assertEquals("/topic/adventure/webrtc/{adventureId}", child(channels, "adventureWebrtc").get("address"));
    }

    @Test
    void every_payload_is_an_openapi_schema_that_exists() throws IOException {
        Map<String, Object> spec = load(ASYNCAPI);
        Map<String, Object> schemas = child(child(load(OPENAPI), "components"), "schemas");

        Map<String, Object> messages = child(child(spec, "components"), "messages");
        Assertions.assertEquals(2, messages.size());
        messages.forEach((name, message) -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = child((Map<String, Object>) message, "payload");
            String ref = String.valueOf(payload.get("$ref"));
            Assertions.assertTrue(ref.startsWith("openapi.yaml#/components/schemas/"), name + ": " + ref);
            Assertions.assertTrue(schemas.containsKey(ref.substring(ref.lastIndexOf('/') + 1)), name + ": " + ref);
        });
        Assertions.assertEquals("openapi.yaml#/components/schemas/MessageDto",
                child(child(messages, "chatMessage"), "payload").get("$ref"));
        Assertions.assertEquals("openapi.yaml#/components/schemas/WebRTCMessage",
                child(child(messages, "webrtcSignal"), "payload").get("$ref"));
    }

    // ===== the file is tied to the code: it cannot drift without a red test =====

    private static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");

    /** The value of a property, or its default when it is written {@code ${VARIABLE:default}}. */
    private static String property(String file, String key) throws IOException {
        Pattern line = Pattern.compile("^" + Pattern.quote(key) + "=(.*)$", Pattern.MULTILINE);
        Matcher found = line.matcher(Files.readString(MAIN_RESOURCES.resolve(file)));
        Assertions.assertTrue(found.find(), key + " is missing from " + file);
        Matcher withDefault = Pattern.compile("\\$\\{[A-Z_]+:([^}]*)}").matcher(found.group(1));
        return withDefault.find() ? withDefault.group(1) : found.group(1);
    }

    @Test
    void the_documented_addresses_are_exactly_the_topics_the_server_accepts() throws IOException {
        Map<String, Object> channels = child(load(ASYNCAPI), "channels");
        UUID adventureId = UUID.randomUUID();
        Assertions.assertEquals(2, channels.size());
        channels.forEach((name, channel) -> {
            @SuppressWarnings("unchecked")
            String address = String.valueOf(((Map<String, Object>) channel).get("address"));
            Assertions.assertEquals(Optional.of(adventureId),
                    AdventureTopics.adventureIdOf(address.replace("{adventureId}", adventureId.toString())), name);
        });
    }

    @Test
    void the_documented_subscription_limit_is_the_configured_one() throws IOException {
        Object documented = child(load(ASYNCAPI), "info").get("x-subscriptions-per-connection");
        Assertions.assertNotNull(documented);
        for (String profile : new String[]{"application-dev.properties", "application-prod.properties"}) {
            Assertions.assertEquals(String.valueOf(documented),
                    property(profile, "limits.websocket.subscriptions-per-connection"), profile);
        }
    }

    @Test
    void the_documented_endpoint_port_and_context_path_are_the_configured_ones() throws IOException {
        Map<String, Object> server = child(child(load(ASYNCAPI), "servers"), "local");
        String contextPath = property("application-dev.properties", "server.servlet.context-path");
        Assertions.assertEquals("localhost:" + property("application-dev.properties", "server.port"), server.get("host"));
        Assertions.assertEquals("/" + contextPath.replaceFirst("^/", "") + WebSocketConfig.ENDPOINT, server.get("pathname"));
    }
}
