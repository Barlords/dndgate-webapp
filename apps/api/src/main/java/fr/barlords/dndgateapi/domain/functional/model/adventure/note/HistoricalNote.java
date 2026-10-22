package fr.barlords.dndgateapi.domain.functional.model.adventure.note;

import fr.barlords.dndgateapi.domain.functional.model.adventure.Adventure;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.LocalDate;
import java.util.UUID;

@Value
@Builder
public class HistoricalNote {

    @Builder.Default
    @With
    UUID id = UUID.randomUUID();

    @With
    Adventure adventure;

    @With
    String content;

    @With
    LocalDate date;

    /** Version read from the storage, null while the note has never been saved: it detects concurrent changes. */
    @With
    Long version;
}
