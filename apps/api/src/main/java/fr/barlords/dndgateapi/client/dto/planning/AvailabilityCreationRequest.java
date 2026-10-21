package fr.barlords.dndgateapi.client.dto.planning;

import fr.barlords.dndgateapi.client.validator.ValidDate;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record AvailabilityCreationRequest(
        @NotNull UUID planningId,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @ValidDate LocalDate> propositionDates
){
}
