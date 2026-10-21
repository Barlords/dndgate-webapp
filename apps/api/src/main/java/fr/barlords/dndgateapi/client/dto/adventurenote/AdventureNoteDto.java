package fr.barlords.dndgateapi.client.dto.adventurenote;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.NoteVisibility;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record AdventureNoteDto(
    @NotNull UUID id,
    @NotNull String content,
    @NotNull LocalDate date,
    @NotNull NoteVisibility noteVisibility
) {}
