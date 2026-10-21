package fr.barlords.dndgateapi.client.dto.historicalnote;

import fr.barlords.dndgateapi.client.validator.ValidDate;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record HistoricalNoteCreationRequest(
        @NotNull UUID adventureId,
        @NotBlank @Size(min = 1, max = 5000) String content,
        @NotNull @ValidDate LocalDate date
) {}
