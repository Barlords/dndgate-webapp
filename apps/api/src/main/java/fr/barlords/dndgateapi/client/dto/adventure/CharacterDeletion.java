package fr.barlords.dndgateapi.client.dto.adventure;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record CharacterDeletion(
        @NotNull UUID characterId,
        @NotNull @Size(max = 300) String confirmation
) { }
