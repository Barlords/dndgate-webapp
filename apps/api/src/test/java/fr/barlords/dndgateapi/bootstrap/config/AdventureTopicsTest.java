package fr.barlords.dndgateapi.bootstrap.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.UUID;

class AdventureTopicsTest {

    private static final String ID = "3f2b8c1e-9a4d-4e7b-8c2a-1d5e6f7a8b9c";

    @Test
    void the_chat_and_the_signals_of_an_adventure_are_the_only_topics() {
        Assertions.assertEquals(Optional.of(UUID.fromString(ID)), AdventureTopics.adventureIdOf("/topic/adventure/chat/" + ID));
        Assertions.assertEquals(Optional.of(UUID.fromString(ID)), AdventureTopics.adventureIdOf("/topic/adventure/webrtc/" + ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/topic/adventure/x/" + ID,
            "/topic/adventure/" + ID,
            "/topic/adventure/chat/" + ID + "/extra",
            "/topic/adventure/chat/" + ID + "/",
            "/topic/adventure/chat/x/" + ID,
            "/topic/adventure/chat//" + ID,
            "/topic/adventure/chat/" + ID + " ",
            " /topic/adventure/chat/" + ID,
            "/topic/adventure/chat/3F2B8C1E-9A4D-4E7B-8C2A-1D5E6F7A8B9C",
            "/topic/adventure/chat/3f2b8c1e9a4d4e7b8c2a1d5e6f7a8b9c",
            "/topic/adventure/chat/3f2b8c1e-9a4d-4e7b-8c2a-1d5e6f7a8b9",
            "/topic/adventure/chat/*",
            "/topic/adventure/webrtc/**",
            "/topic/adventure/CHAT/" + ID,
            "/topic/other/chat/" + ID,
            "/app/adventure/chat/" + ID,
            "topic/adventure/chat/" + ID,
            "/topic/adventure/chat/" + ID + "\n",
            ""})
    void anything_else_is_refused(String destination) {
        Assertions.assertEquals(Optional.empty(), AdventureTopics.adventureIdOf(destination), destination);
    }

    @Test
    void no_destination_is_refused() {
        Assertions.assertEquals(Optional.empty(), AdventureTopics.adventureIdOf(null));
    }
}
