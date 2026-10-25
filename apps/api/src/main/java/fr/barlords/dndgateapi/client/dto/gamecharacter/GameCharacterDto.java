package fr.barlords.dndgateapi.client.dto.gamecharacter;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import fr.barlords.dndgateapi.domain.functional.model.CharacterVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record GameCharacterDto(
        UUID id,
        String name,
        String race,
        String martialClass,
        String personality,
        String background,
        /** Where to read the picture (needs the same authentication as the rest of the API), null without one. */
        @Schema(nullable = true) String pictureUrl,
        UUID ownerId,
        /** Null for a character kept outside any adventure of the application. */
        UUID adventureId,
        /** True for the character the owner currently plays in the adventure. */
        boolean active,
        /** True for a non-player character of the game master. */
        boolean npc,
        /** PRIVATE (only the game master reads it) or PUBLIC. */
        CharacterVisibility visibility
) {
}
