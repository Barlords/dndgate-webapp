package fr.barlords.dndgateapi.domain.functional.model;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class Message {

    @Builder.Default
    UUID id = UUID.randomUUID();

    @With
    UUID adventureId;

    @With
    UUID senderId;

    @With
    String content;

    @With
    LocalDateTime sendDate;

}
