package fr.barlords.dndgateapi.client.dto.adventure;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The characters to delete instead of keeping them in the reserve: each one needs its retyped name. */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record QuitAdventureRequest(
        @Valid @Size(max = 10) List<CharacterDeletion> deletions
) { }
