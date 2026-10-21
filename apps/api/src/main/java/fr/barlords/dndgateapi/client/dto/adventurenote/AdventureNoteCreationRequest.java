package fr.barlords.dndgateapi.client.dto.adventurenote;

import fr.barlords.dndgateapi.client.validator.ValidDate;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.NoteVisibility;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record AdventureNoteCreationRequest(
        @NotNull UUID adventureId,
        @NotBlank @Size(min = 1, max = 5000) String content,
        @NotNull @ValidDate LocalDate date,
        @NotNull NoteVisibility visibility
) {}
