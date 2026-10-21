package fr.barlords.dndgateapi.domain.functional.model.planning;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class Availability {

    @Builder.Default
    @With
    UUID id = UUID.randomUUID();

    @With
    UUID planningId;

    @With
    UUID accountId;

    @Builder.Default
    @With
    List<LocalDate> availabilityDates = new ArrayList<>();
}
