package fr.barlords.dndgateapi.client.dto.gamecharacter;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record CharacterAdventureRequest(
        @NotNull UUID adventureId
) { }
