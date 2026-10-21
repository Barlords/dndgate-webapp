package fr.barlords.dndgateapi.domain.functional.model;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.util.UUID;

@Value
@Builder
public class Rating {

    @Builder.Default
    @With
    UUID id = UUID.randomUUID();

    @With
    UUID emitterId;

    @With
    UUID accountTargetedId;

    @With
    int rate;

}
