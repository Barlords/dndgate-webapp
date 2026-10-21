package fr.barlords.dndgateapi.client.dto.adventurenote;

import fr.barlords.dndgateapi.client.validator.ValidDate;
import fr.barlords.dndgateapi.client.validator.Patterns;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import fr.barlords.dndgateapi.domain.functional.model.adventure.note.NoteVisibility;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record AdventureNotePatchRequest(
        UUID adventureId,
        @Pattern(regexp = Patterns.NOT_BLANK) @Size(max = 5000) String content,
        @ValidDate LocalDate date,
        NoteVisibility visibility
) {}
