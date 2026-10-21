package fr.barlords.dndgateapi.client.dto.historicalnote;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record HistoricalNoteDto(
    @NotNull UUID id,
    @NotNull String content,
    @NotNull LocalDate date
) {}
