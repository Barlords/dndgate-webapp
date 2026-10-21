package fr.barlords.dndgateapi.client.dto.planning;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record AvailabilityDto(
        UUID id,
        UUID planningId,
        UUID accountId,
        List<LocalDate> availabilityDates
) {}
