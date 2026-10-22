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
public class Planning {

    @Builder.Default
    @With
    UUID id = UUID.randomUUID();

    @With
    UUID adventureId;

    @Builder.Default
    @With
    List<LocalDate> propositionDates = new ArrayList<>();

    /** Version read from the storage, null while the planning has never been saved: it detects concurrent changes. */
    @With
    Long version;
}
