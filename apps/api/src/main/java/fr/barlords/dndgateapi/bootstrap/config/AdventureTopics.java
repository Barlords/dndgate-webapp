package fr.barlords.dndgateapi.bootstrap.config;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The only destinations a client may subscribe to: {@code /topic/adventure/chat/{id}} and {@code /topic/adventure/webrtc/{id}},
 * written exactly as the server publishes them (lowercase UUID, nothing before or after).
 * Any other spelling would either receive nothing or match more than one adventure: it is refused.
 */
final class AdventureTopics {

    private static final Pattern TOPIC = Pattern.compile(
            "/topic/adventure/(?:chat|webrtc)/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");

    private AdventureTopics() {
    }

    /** The adventure of the topic, or empty when the destination is not exactly one of the adventure topics. */
    static Optional<UUID> adventureIdOf(String destination) {
        if (destination == null) {
            return Optional.empty();
        }
        Matcher matcher = TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(matcher.group(1)));
    }
}
