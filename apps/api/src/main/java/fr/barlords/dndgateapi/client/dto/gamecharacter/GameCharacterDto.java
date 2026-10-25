package fr.barlords.dndgateapi.client.dto.gamecharacter;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record GameCharacterDto(
        UUID id,
        String name,
        String race,
        String martialClass,
        String personality,
        String background,
        /** Where to read the picture (needs the same authentication as the rest of the API). */
        String pictureUrl,
        UUID ownerId,
        /** Null for a character kept outside any adventure of the application. */
        UUID adventureId,
        /** True for the character the owner currently plays in the adventure. */
        boolean active
) {
}
