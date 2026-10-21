package fr.barlords.dndgateapi.client.dto.planning;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record PlanningDto(
        UUID id,
        UUID adventureId,
        List<LocalDate> propositionDates
) {}
